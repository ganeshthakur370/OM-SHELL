
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The core shell session manager for OM Shell.
 * Maintains environment variables, local variables, aliases, history,
 * working directory, background jobs, and execution pipelines.
 */
public class Shell {

    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$", Pattern.DOTALL);

    private Path workingDirectory;
    private final Map<String, String> variables = new LinkedHashMap<>();
    private final Set<String> exportedVariables = new HashSet<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, List<String>> functions = new LinkedHashMap<>();
    private final Map<String, String> traps = new LinkedHashMap<>();
    private final Map<String, List<String>> indexedArrays = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> associativeArrays = new LinkedHashMap<>();
    private final SignalManager signalManager;
    private final List<String> history = new ArrayList<>();
    private final Path historyFile;

    private final JobManager jobManager = new JobManager();
    private final BuiltinRegistry builtinRegistry = new BuiltinRegistry();
    private final PipelineExecutor pipelineExecutor;
    private final ScriptEngine scriptEngine;

    private List<String> positionalParameters = new ArrayList<>();
    private String scriptName = "omsh";
    private int lastExitCode = 0;
    private boolean running = true;

    private InputStream standardInput = System.in;
    private PrintStream standardOutput = System.out;
    private PrintStream standardError = System.err;

    public Shell() {
        this.workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        this.historyFile = Path.of(System.getProperty("user.home"), ".omsh_history");

        // Inherit host environment variables
        Map<String, String> hostEnv = System.getenv();
        for (Map.Entry<String, String> entry : hostEnv.entrySet()) {
            variables.put(entry.getKey(), entry.getValue());
            exportedVariables.add(entry.getKey());
        }

        // Set default shell variables
        variables.put("PWD", workingDirectory.toString());
        variables.put("SHELL", "omsh");
        variables.put("OM_SHELL", "1");
        exportedVariables.add("PWD");
        exportedVariables.add("SHELL");
        exportedVariables.add("OM_SHELL");

        this.pipelineExecutor = new PipelineExecutor(this);
        this.scriptEngine = new ScriptEngine(this);
        this.signalManager = new SignalManager(this);

        loadHistory();
        setupShutdownHook();
    }

    /**
     * Starts the interactive shell REPL.
     */
    public void startInteractive() {
        printBanner();
        BufferedReader reader = new BufferedReader(new InputStreamReader(standardInput, StandardCharsets.UTF_8));

        while (running) {
            printPrompt();
            String rawLine;
            try {
                rawLine = reader.readLine();
                if (rawLine == null) {
                    // EOF (Ctrl-D)
                    break;
                }
            } catch (IOException e) {
                break;
            }

            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            addHistory(rawLine);

            try {
                // If the line starts a multi-line script block (if, for, while),
                // pass it to the script engine with interactive line cursor
                if (isBlockOpener(line)) {
                    List<String> blockLines = new ArrayList<>();
                    blockLines.add(line);
                    readMultiLineBlock(reader, line, blockLines);
                    ScriptEngine.LineCursor cursor = new ScriptEngine.LineCursor(blockLines);
                    cursor.next(); // consume first line
                    lastExitCode = scriptEngine.executeBlockOrStatement(line, cursor);
                } else {
                    lastExitCode = executeLine(line);
                }
            } catch (Exception e) {
                standardError.println("omsh: " + e.getMessage());
                lastExitCode = 1;
            }
        }
    }

    private void printBanner() {
        standardOutput.println("Welcome to OM Shell Ultimate (Java Edition)");
        standardOutput.println("Type 'help' to view available built-in commands or 'exit' to quit.");
        standardOutput.println();
    }

    private void printPrompt() {
        String pathStr = workingDirectory.toString();
        String userHome = System.getProperty("user.home");
        if (pathStr.startsWith(userHome)) {
            pathStr = "~" + pathStr.substring(userHome.length());
        }

        // Use ANSI colors if supported or standard prompt
        standardOutput.printf("om:%s> ", pathStr);
        standardOutput.flush();
    }

    private boolean isBlockOpener(String line) {
        return line.startsWith("if ") || line.equals("if")
                || line.startsWith("for ") || line.equals("for")
                || line.startsWith("while ") || line.equals("while")
                || line.startsWith("until ") || line.equals("until")
                || line.matches("^[A-Za-z_][A-Za-z0-9_-]*\\s*\\(\\)\\s*\\{?\\s*$");
    }

    private void readMultiLineBlock(BufferedReader reader, String firstLine, List<String> blockLines) throws IOException {
        int depth = 1;
        String terminator = "done";
        if (firstLine.startsWith("if ") || firstLine.equals("if")) {
            terminator = "fi";
        } else if (firstLine.contains("{") || firstLine.contains("()")) {
            terminator = "}";
        }

        while (depth > 0 && running) {
            standardOutput.print("OM Shell> ");
            standardOutput.flush();

            String line = reader.readLine();
            if (line == null) break;

            blockLines.add(line);
            String trimmed = line.trim();

            if (terminator.equals("fi")) {
                if (trimmed.startsWith("if ") || trimmed.equals("if")) depth++;
                else if (trimmed.equals("fi") || trimmed.startsWith("fi ") || trimmed.startsWith("fi;")) depth--;
            } else if (terminator.equals("done")) {
                if (trimmed.startsWith("for ") || trimmed.startsWith("while ") || trimmed.startsWith("until ")) depth++;
                else if (trimmed.equals("done") || trimmed.startsWith("done ") || trimmed.startsWith("done;")) depth--;
            } else if (terminator.equals("}")) {
                if (trimmed.contains("{")) depth++;
                if (trimmed.equals("}") || trimmed.startsWith("} ")) depth--;
            }
        }
    }

    /**
     * Executes a single logical shell command line statement, handling:
     * - Variable assignment (NAME=value)
     * - Alias expansion
     * - Command substitution $(...)
     * - Arithmetic expansion $((...))
     * - Variable expansion $NAME, $?, etc.
     * - Chained execution (&&, ||, ;)
     * - Pipelines (|) and redirection
     */
    public int executeLine(String rawLine) {
        if (rawLine == null) return 0;
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) return 0;

        if (handleArrayAssignment(line)) {
            lastExitCode = 0;
            return 0;
        }

        // Check if line contains block constructs (if, for, while, until, function)
        if (ScriptEngine.containsBlockConstruct(line)) {
            return scriptEngine.executeScriptSource(rawLine, null, null);
        }

        // Check for shell assignment: NAME=value (without spaces around '=')
        Matcher assignMatcher = ASSIGNMENT_PATTERN.matcher(line);
        if (assignMatcher.matches()) {
            String varName = assignMatcher.group(1);
            String rawVal = assignMatcher.group(2).trim();

            // Expand substitution and variables inside value
            String expandedVal = expand(rawVal);
            String cleanVal = unquoteAssignmentValue(expandedVal);

            setVariable(varName, cleanVal, false);
            lastExitCode = 0;
            return 0;
        }

        // Expand aliases at command start
        line = Expander.expandAliases(line, this);

        // Expand arithmetic $((expr)) FIRST
        line = Expander.expandArithmetic(line, this);

        // Expand command substitutions $(cmd) or `cmd`
        line = Expander.expandCommandSubstitution(line, this);

        // Expand variables $VAR, $?, $$
        line = Expander.expandVariables(line, this);

        // Parse into token stream and command line chain
        Lexer lexer = new Lexer(line);
        List<Token> tokens = lexer.tokenize();
        CommandLineParser parser = new CommandLineParser(tokens);
        CommandLine commandLine = parser.parse();

        if (commandLine == null) {
            return 0;
        }

        // Execute chain of statements
        CommandLine current = commandLine;
        CommandLine.Operator previousOperator = CommandLine.Operator.NONE;

        while (current != null && running) {
            boolean shouldExecute = true;

            if (previousOperator == CommandLine.Operator.AND_IF) {
                shouldExecute = (lastExitCode == 0);
            } else if (previousOperator == CommandLine.Operator.OR_IF) {
                shouldExecute = (lastExitCode != 0);
            }

            if (shouldExecute) {
                lastExitCode = pipelineExecutor.execute(current);
            }

            previousOperator = current.getNextOperator();
            current = current.getNextCommand();
        }

        return lastExitCode;
    }

    /**
     * Executes a command string in a subshell environment and returns its captured standard output.
     */
    public String executeSubshell(String command) {
        ByteArrayOutputStream subOut = new ByteArrayOutputStream();
        PrintStream oldOut = standardOutput;
        PrintStream tempOut = new PrintStream(subOut, true, StandardCharsets.UTF_8);

        this.standardOutput = tempOut;
        try {
            executeLine(command);
        } finally {
            tempOut.flush();
            this.standardOutput = oldOut;
        }

        return subOut.toString(StandardCharsets.UTF_8);
    }

    /**
     * Executes a script file from disk.
     */
    public int executeScript(Path scriptPath, String[] args) throws IOException {
        return scriptEngine.executeFile(scriptPath, args);
    }

    /**
     * Performs standard expansions on a string.
     */
    public String expand(String text) {
        String res = Expander.expandArithmetic(text, this);
        res = Expander.expandCommandSubstitution(res, this);
        res = Expander.expandVariables(res, this);
        return res;
    }

    private String unquoteAssignmentValue(String value) {
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    // Path resolution
    public Path resolvePath(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) {
            return workingDirectory;
        }
        String expanded = Expander.expandTilde(pathStr);
        Path p = Path.of(expanded);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        return workingDirectory.resolve(p).normalize();
    }

    public Path findExecutable(String name) {
        return ProcessLauncher.resolveExecutable(name, this);
    }

    // Variable management
    public void setVariable(String name, String value, boolean export) {
        variables.put(name, value);
        if (export) {
            exportedVariables.add(name);
        }
    }

    public String getVariable(String name) {
        return variables.get(name);
    }

    public void removeVariable(String name) {
        variables.remove(name);
        exportedVariables.remove(name);
        indexedArrays.remove(name);
        associativeArrays.remove(name);
    }

    public Map<String, String> getVariables() {
        return Collections.unmodifiableMap(variables);
    }

    public boolean isExported(String name) {
        return exportedVariables.contains(name);
    }

    // Array management
    public void setIndexedArray(String name, List<String> values) {
        indexedArrays.put(name, new ArrayList<>(values));
        associativeArrays.remove(name);
    }

    public List<String> getIndexedArray(String name) {
        return indexedArrays.getOrDefault(name, List.of());
    }

    public String getIndexedArrayValue(String name, int index) {
        List<String> values = indexedArrays.get(name);
        if (values == null || index < 0 || index >= values.size()) return "";
        return values.get(index);
    }

    public void setAssociativeArray(String name, Map<String, String> values) {
        associativeArrays.put(name, new LinkedHashMap<>(values));
        indexedArrays.remove(name);
    }

    public Map<String, String> getAssociativeArray(String name) {
        return Collections.unmodifiableMap(associativeArrays.getOrDefault(name, Map.of()));
    }

    public String getAssociativeArrayValue(String name, String key) {
        return associativeArrays.getOrDefault(name, Map.of()).getOrDefault(key, "");
    }

    public boolean isIndexedArray(String name) { return indexedArrays.containsKey(name); }
    public boolean isAssociativeArray(String name) { return associativeArrays.containsKey(name); }

    public void removeArrayElement(String name, String index) {
        if (indexedArrays.containsKey(name)) {
            try {
                int position = Integer.parseInt(index);
                List<String> values = indexedArrays.get(name);
                if (position >= 0 && position < values.size()) values.set(position, "");
            } catch (NumberFormatException ignored) { }
        } else if (associativeArrays.containsKey(name)) {
            associativeArrays.get(name).remove(index);
        }
    }

    private boolean handleArrayAssignment(String line) {
        java.util.regex.Matcher appendMatcher = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\+=\\((.*)\\)$", Pattern.DOTALL).matcher(line.trim());
        if (appendMatcher.matches()) {
            String name = appendMatcher.group(1);
            List<String> values = new ArrayList<>(getIndexedArray(name));
            List<String> additions = new Lexer(appendMatcher.group(2)).tokenize().stream()
                    .filter(token -> token.getType() == TokenType.WORD)
                    .map(Token::getValue).toList();
            for (String value : additions) values.add(expand(value));
            setIndexedArray(name, values);
            return true;
        }

        java.util.regex.Matcher elementMatcher = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\[([^]]+)]\\s*=(.*)$", Pattern.DOTALL).matcher(line.trim());
        if (elementMatcher.matches()) {
            String name = elementMatcher.group(1);
            String index = elementMatcher.group(2);
            String value = unquoteAssignmentValue(expand(elementMatcher.group(3).trim()));
            if (index.matches("[0-9]+")) {
                List<String> values = new ArrayList<>(getIndexedArray(name));
                int position = Integer.parseInt(index);
                while (values.size() <= position) values.add("");
                values.set(position, value);
                setIndexedArray(name, values);
            } else {
                Map<String, String> values = new LinkedHashMap<>(getAssociativeArray(name));
                values.put(index, value);
                setAssociativeArray(name, values);
            }
            return true;
        }

        java.util.regex.Matcher matcher = Pattern.compile("^(?:declare\\s+(-[aA])\\s+)?([A-Za-z_][A-Za-z0-9_]*)=\\((.*)\\)$", Pattern.DOTALL).matcher(line.trim());
        if (!matcher.matches()) return false;

        String declaration = matcher.group(1);
        String name = matcher.group(2);
        String contents = matcher.group(3).trim();
        List<String> words = new Lexer(contents).tokenize().stream()
                .filter(token -> token.getType() == TokenType.WORD)
                .map(Token::getValue).toList();

        if ("-A".equals(declaration) || contents.matches("(?s).*\\[[^]]+]=.*")) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String word : words) {
                int equals = word.indexOf('=');
                if (word.startsWith("[") && equals > 0) {
                    String key = word.substring(1, equals - 1);
                    values.put(key, expand(word.substring(equals + 1)));
                }
            }
            setAssociativeArray(name, values);
        } else {
            List<String> values = new ArrayList<>();
            for (String word : words) values.add(expand(word));
            setIndexedArray(name, values);
        }
        return true;
    }

    // Alias management
    public void setAlias(String name, String value) {
        aliases.put(name, value);
    }

    public String getAlias(String name) {
        return aliases.get(name);
    }

    public void removeAlias(String name) {
        aliases.remove(name);
    }

    public void clearAliases() {
        aliases.clear();
    }

    public Map<String, String> getAliases() {
        return Collections.unmodifiableMap(aliases);
    }

    // Function management
    public void registerFunction(String name, List<String> bodyLines) {
        functions.put(name, new ArrayList<>(bodyLines));
    }

    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    public List<String> getFunction(String name) {
        return functions.get(name);
    }

    // Trap management
    public void registerTrap(String signal, String command) {
        traps.put(signal.toUpperCase(Locale.ROOT), command);
    }

    public Map<String, String> getTraps() {
        return Collections.unmodifiableMap(traps);
    }

    public boolean hasTrap(String signal) {
        return traps.containsKey(signal.toUpperCase(Locale.ROOT));
    }

    public void removeTrap(String signal) {
        traps.remove(signal.toUpperCase(Locale.ROOT));
    }

    public void handleSignal(String signal) {
        String normalized = signal.toUpperCase(Locale.ROOT);
        if (hasTrap(normalized)) {
            triggerTrap(normalized);
        } else if (normalized.equals("INT")) {
            lastExitCode = 130;
        } else if (normalized.equals("TERM")) {
            running = false;
            lastExitCode = 143;
        }
    }

    public void triggerTrap(String signal) {
        String cmd = traps.get(signal);
        if (cmd != null) {
            try {
                executeLine(cmd);
            } catch (Exception ignored) {}
        }
    }

    // History management
    public void addHistory(String cmd) {
        if (cmd == null || cmd.isBlank()) return;
        history.add(cmd);
        try {
            Files.writeString(historyFile, cmd + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    public List<String> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void clearHistory() {
        history.clear();
        try {
            Files.deleteIfExists(historyFile);
        } catch (IOException ignored) {}
    }

    private void loadHistory() {
        if (Files.exists(historyFile)) {
            try {
                history.addAll(Files.readAllLines(historyFile, StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        }
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> triggerTrap("EXIT"), "om-exit-hook"));
    }

    public SignalManager getSignalManager() { return signalManager; }

    // Getters and Setters
    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(Path dir) {
        this.workingDirectory = dir;
    }

    public int getLastExitCode() {
        return lastExitCode;
    }

    public void setLastExitCode(int code) {
        this.lastExitCode = code;
    }

    public long getLastBackgroundPid() {
        return jobManager.getLastStartedPid();
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public BuiltinRegistry getBuiltinRegistry() {
        return builtinRegistry;
    }

    public List<String> getPositionalParameters() {
        return positionalParameters;
    }

    public void setPositionalParameters(List<String> positionalParameters) {
        this.positionalParameters = positionalParameters != null ? new ArrayList<>(positionalParameters) : new ArrayList<>();
    }

    public String getPositionalParameter(int index) {
        if (index == 0) return scriptName;
        int target = index - 1;
        if (target >= 0 && target < positionalParameters.size()) {
            return positionalParameters.get(target);
        }
        return "";
    }

    public int getPositionalParameterCount() {
        return positionalParameters.size();
    }

    public String getScriptName() {
        return scriptName;
    }

    public void setScriptName(String scriptName) {
        this.scriptName = scriptName != null ? scriptName : "omsh";
    }

    public InputStream getStandardInput() {
        return standardInput;
    }

    public void setStandardInput(InputStream standardInput) {
        this.standardInput = standardInput;
    }

    public PrintStream getStandardOutput() {
        return standardOutput;
    }

    public void setStandardOutput(PrintStream standardOutput) {
        this.standardOutput = standardOutput;
    }

    public PrintStream getStandardError() {
        return standardError;
    }

    public void setStandardError(PrintStream standardError) {
        this.standardError = standardError;
    }
}
