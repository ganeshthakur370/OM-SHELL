import java.io.*;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.StandardOpenOption;

import java.util.*;

import java.util.concurrent.ConcurrentHashMap;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



public class Shell {



    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$", Pattern.DOTALL);

    private static final Pattern COMPOUND_ASSIGNMENT_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\+?=\\((.*)\\)$", Pattern.DOTALL);

    private static final Pattern ELEMENT_ASSIGNMENT_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\[([^\\]]+)\\]\\+?=(.*)$", Pattern.DOTALL);



    private Path workingDirectory;

    private final Map<String, String> variables = new LinkedHashMap<>();

    private final Set<String> exportedVariables = new HashSet<>();

    private final Set<String> readonlyVariables = new HashSet<>();

    private final Set<String> integerVariables = new HashSet<>();



    private final Map<String, TreeMap<Integer, String>> indexedArrays = new LinkedHashMap<>();



    private final Map<String, LinkedHashMap<String, String>> associativeArrays = new LinkedHashMap<>();



    private final Map<String, String> aliases = new LinkedHashMap<>();

    private final Map<String, List<String>> functions = new LinkedHashMap<>();

    private final Map<String, String> traps = new LinkedHashMap<>();

    private final Map<String, Boolean> shellOptions = new LinkedHashMap<>();



    private final List<String> history = new ArrayList<>();

    private final Path historyFile;



    private final JobManager jobManager = new JobManager();

    private final BuiltinRegistry builtinRegistry = new BuiltinRegistry();

    private final SignalManager signalManager;

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



        Map<String, String> hostEnv = System.getenv();

        for (Map.Entry<String, String> entry : hostEnv.entrySet()) {

            variables.put(entry.getKey(), entry.getValue());

            exportedVariables.add(entry.getKey());

        }



        variables.put("PWD", workingDirectory.toString());

        variables.put("SHELL", "omsh");

        variables.put("OM_SHELL", "3.0");

        variables.put("BASH_VERSION", "5.2.0(1)-omsh-3.0");

        exportedVariables.add("PWD");

        exportedVariables.add("SHELL");

        exportedVariables.add("OM_SHELL");



        shellOptions.put("errexit", false);

        shellOptions.put("nounset", false);

        shellOptions.put("xtrace", false);

        shellOptions.put("pipefail", false);

        shellOptions.put("expand_aliases", true);



        this.pipelineExecutor = new PipelineExecutor(this);

        this.scriptEngine = new ScriptEngine(this);

        this.signalManager = new SignalManager(this);



        loadHistory();

        setupShutdownHook();

    }



    public void startInteractive() {

        printBanner();

        BufferedReader reader = new BufferedReader(new InputStreamReader(standardInput, StandardCharsets.UTF_8));



        while (running) {

            processPendingSignals();

            printPrompt();



            String rawLine;

            try {

                rawLine = reader.readLine();

                if (rawLine == null) {

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

                if (ScriptEngine.containsBlockConstruct(line)) {

                    List<String> blockLines = new ArrayList<>();

                    blockLines.add(line);

                    readMultiLineBlock(reader, line, blockLines);

                    lastExitCode = scriptEngine.executeScriptSource(String.join("\n", blockLines), null, null);

                } else {

                    lastExitCode = executeLine(line);

                }

            } catch (Exception e) {

                standardError.println("omsh: " + e.getMessage());

                lastExitCode = 1;

            }

        }



        triggerTrap("EXIT");

    }



    private void printBanner() {

        standardOutput.println("Welcome to OM Shell Ultimate 3.0 (Modular Advanced Java Engine)");

        standardOutput.println("Type 'help' to view available built-in commands or 'exit' to quit.");

        standardOutput.println();

    }



    private void printPrompt() {

        String pathStr = workingDirectory.toString();

        String userHome = System.getProperty("user.home");

        if (pathStr.startsWith(userHome)) {

            pathStr = "~" + pathStr.substring(userHome.length());

        }

        standardOutput.printf("om:%s> ", pathStr);

        standardOutput.flush();

    }



    private void readMultiLineBlock(BufferedReader reader, String firstLine, List<String> blockLines) throws IOException {

        int depth = 1;

        String terminator = "done";

        if (firstLine.startsWith("if ") || firstLine.equals("if")) {

            terminator = "fi";

        } else if (firstLine.startsWith("case ") || firstLine.equals("case")) {

            terminator = "esac";

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

            } else if (terminator.equals("esac")) {

                if (trimmed.startsWith("case ") || trimmed.equals("case")) depth++;

                else if (trimmed.equals("esac") || trimmed.startsWith("esac ") || trimmed.startsWith("esac;")) depth--;

            } else if (terminator.equals("}")) {

                if (trimmed.contains("{")) depth++;

                if (trimmed.equals("}") || trimmed.startsWith("} ")) depth--;

            }

        }

    }



    public int executeLine(String rawLine) {

        if (rawLine == null) return 0;

        String line = rawLine.trim();

        if (line.isEmpty() || line.startsWith("#")) return 0;



        processPendingSignals();



        if (isOptionEnabled("xtrace")) {

            standardError.println("+ " + line);

        }



        if (line.startsWith("((") && line.endsWith("))")) {

            String expr = line.substring(2, line.length() - 2).trim();

            try {

                long val = ArithmeticEvaluator.evaluate(expr, variables);

                lastExitCode = (val != 0) ? 0 : 1;

                return lastExitCode;

            } catch (Exception e) {

                standardError.println("omsh: " + e.getMessage());

                lastExitCode = 1;

                return 1;

            }

        }



        if (line.startsWith("{") && line.endsWith("}") && !line.startsWith("${")) {

            String inner = line.substring(1, line.length() - 1).trim();

            for (String subLine : ScriptEngine.splitIntoLogicalLines(inner)) {

                lastExitCode = executeLine(subLine);

            }

            return lastExitCode;

        }



        if (line.startsWith("(") && line.endsWith(")") && !line.startsWith("((") && !line.contains("()")) {

            String inner = line.substring(1, line.length() - 1).trim();

            Shell subshell = createSubshell();

            lastExitCode = subshell.executeLine(inner);

            return lastExitCode;

        }



        if (ScriptEngine.containsBlockConstruct(line)) {

            return scriptEngine.executeScriptSource(rawLine, null, null);

        }

        if (rawLine.contains("\n") || rawLine.contains("\r")) {

            return scriptEngine.executeScriptSource(rawLine, null, null);

        }



        Matcher compoundAssign = COMPOUND_ASSIGNMENT_PATTERN.matcher(line);

        if (compoundAssign.matches()) {

            String name = compoundAssign.group(1);

            boolean append = line.contains("+=");

            String content = compoundAssign.group(2).trim();

            handleCompoundArrayAssignment(name, content, append);

            lastExitCode = 0;

            return 0;

        }



        Matcher elemAssign = ELEMENT_ASSIGNMENT_PATTERN.matcher(line);

        if (elemAssign.matches()) {

            String name = elemAssign.group(1);

            String keyOrIndex = elemAssign.group(2);

            boolean append = line.contains("+=");

            String val = unquoteAssignmentValue(expand(elemAssign.group(3).trim()));



            if (isAssociativeArray(name)) {

                String current = append ? getAssociativeArrayValue(name, keyOrIndex) : "";

                setAssociativeArrayValue(name, keyOrIndex, current + val);

            } else {

                try {

                    int idx = (int) ArithmeticEvaluator.evaluate(keyOrIndex, variables);

                    String current = append ? getIndexedArrayValue(name, idx) : "";

                    setIndexedArrayValue(name, idx, current + val);

                } catch (Exception e) {

                    setIndexedArrayValue(name, 0, val);

                }

            }

            lastExitCode = 0;

            return 0;

        }



        Matcher assignMatcher = ASSIGNMENT_PATTERN.matcher(line);

        if (assignMatcher.matches()) {

            String varName = assignMatcher.group(1);

            String rawVal = assignMatcher.group(2).trim();



            if (readonlyVariables.contains(varName)) {

                standardError.println("omsh: " + varName + ": readonly variable");

                lastExitCode = 1;

                return 1;

            }



            String expandedVal = expand(rawVal);

            String cleanVal = unquoteAssignmentValue(expandedVal);



            if (integerVariables.contains(varName)) {

                try {

                    cleanVal = String.valueOf(ArithmeticEvaluator.evaluate(cleanVal, variables));

                } catch (Exception ignored) {}

            }



            setVariable(varName, cleanVal, false);

            lastExitCode = 0;

            return 0;

        }



        if (isOptionEnabled("expand_aliases")) {

            line = Expander.expandAliases(line, this);

        }



        line = Expander.expandArithmetic(line, this);



        line = Expander.expandCommandSubstitution(line, this);



        line = Expander.expandVariables(line, this);



        Lexer lexer = new Lexer(line);

        List<Token> tokens = lexer.tokenize();

        CommandLineParser parser = new CommandLineParser(tokens);

        CommandLine commandLine = parser.parse();



        if (commandLine == null) {

            return 0;

        }



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

                if (lastExitCode != 0) {

                    triggerTrap("ERR");

                    if (isOptionEnabled("errexit")) {

                        break;

                    }

                }

            }



            previousOperator = current.getNextOperator();

            current = current.getNextCommand();

        }



        return lastExitCode;

    }



    public void handleCompoundArrayAssignment(String name, String content, boolean append) {

        if (!append) {

            indexedArrays.remove(name);

            associativeArrays.remove(name);

        }



        if (content.isEmpty()) {

            if (!isAssociativeArray(name)) {

                ensureIndexedArray(name);

            }

            return;

        }



        if (content.contains("[") && content.contains("]=")) {

            Pattern kvPattern = Pattern.compile("\\[([^\\]]+)\\]=([^\\s\\]]+|\"[^\"]*\"|'[^']*')");

            Matcher m = kvPattern.matcher(content);

            List<Map.Entry<String, String>> pairs = new ArrayList<>();

            boolean allNumeric = true;

            while (m.find()) {

                String k = m.group(1).trim();

                String v = unquoteAssignmentValue(expand(m.group(2)));

                pairs.add(Map.entry(k, v));

                if (!k.matches("-?\\d+")) {

                    allNumeric = false;

                }

            }



            if (isAssociativeArray(name) || !allNumeric) {

                ensureAssociativeArray(name);

                Map<String, String> map = associativeArrays.get(name);

                for (Map.Entry<String, String> pair : pairs) {

                    map.put(pair.getKey(), pair.getValue());

                }

            } else {

                ensureIndexedArray(name);

                TreeMap<Integer, String> array = indexedArrays.get(name);

                for (Map.Entry<String, String> pair : pairs) {

                    try {

                        int idx = Integer.parseInt(pair.getKey());

                        array.put(idx, pair.getValue());

                    } catch (NumberFormatException ignored) {}

                }

            }

        } else {



            ensureIndexedArray(name);

            TreeMap<Integer, String> array = indexedArrays.get(name);

            int nextIdx = array.isEmpty() ? 0 : array.lastKey() + 1;



            List<String> items = splitWordsRespectingQuotes(content);

            for (String rawItem : items) {

                String val = unquoteAssignmentValue(expand(rawItem));

                array.put(nextIdx++, val);

            }

        }

    }



    private List<String> splitWordsRespectingQuotes(String str) {

        List<String> words = new ArrayList<>();

        StringBuilder sb = new StringBuilder();

        char activeQuote = 0;

        boolean esc = false;



        for (int i = 0; i < str.length(); i++) {

            char c = str.charAt(i);

            if (esc) {

                sb.append(c);

                esc = false;

                continue;

            }

            if (c == '\\' && activeQuote != '\'') {

                esc = true;

                continue;

            }

            if (activeQuote != 0) {

                if (c == activeQuote) activeQuote = 0;

                else sb.append(c);

                continue;

            }

            if (c == '\'' || c == '"') {

                activeQuote = c;

                continue;

            }

            if (Character.isWhitespace(c)) {

                if (sb.length() > 0) {

                    words.add(sb.toString());

                    sb.setLength(0);

                }

                continue;

            }

            sb.append(c);

        }

        if (sb.length() > 0) {

            words.add(sb.toString());

        }

        return words;

    }



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



    public int executeScript(Path scriptPath, String[] args) throws IOException {

        return scriptEngine.executeFile(scriptPath, args);

    }



    public int executeSource(String source) {

        return scriptEngine.executeScriptSource(source, scriptName, null);

    }



    public String expand(String text) {

        String res = Expander.expandArithmetic(text, this);

        res = Expander.expandCommandSubstitution(res, this);

        res = Expander.expandVariables(res, this);

        return res;

    }



    public void processPendingSignals() {

        if (signalManager != null) {

            List<String> pending = signalManager.drainPendingSignals();

            for (String sig : pending) {

                triggerTrap(sig);

            }

        }

    }



    public void handleSignal(String signalName) {

        triggerTrap(signalName);

    }



    public void queueSignal(String signalName) {

        if (signalManager != null) {

            signalManager.queueSignal(signalName);

        }

    }



    public boolean isIndexedArray(String name) {

        return indexedArrays.containsKey(name);

    }



    public boolean isAssociativeArray(String name) {

        return associativeArrays.containsKey(name);

    }



    public TreeMap<Integer, String> getIndexedArray(String name) {

        return indexedArrays.computeIfAbsent(name, k -> new TreeMap<>());

    }



    public List<String> getIndexedArrayValues(String name) {

        TreeMap<Integer, String> map = indexedArrays.get(name);

        return map == null ? Collections.emptyList() : new ArrayList<>(map.values());

    }



    public String getIndexedArrayValue(String name, int index) {

        TreeMap<Integer, String> map = indexedArrays.get(name);

        if (map == null || map.isEmpty()) return "";

        if (index < 0) {



            List<String> vals = new ArrayList<>(map.values());

            int pos = vals.size() + index;

            return (pos >= 0 && pos < vals.size()) ? vals.get(pos) : "";

        }

        return map.getOrDefault(index, "");

    }



    public void setIndexedArrayValue(String name, int index, String value) {

        ensureIndexedArray(name);

        indexedArrays.get(name).put(index, value);

    }



    public void ensureIndexedArray(String name) {

        if (!indexedArrays.containsKey(name)) {

            indexedArrays.put(name, new TreeMap<>());

        }

    }



    public Map<String, TreeMap<Integer, String>> getIndexedArrays() {

        return Collections.unmodifiableMap(indexedArrays);

    }



    public Map<String, LinkedHashMap<String, String>> getAssociativeArrays() {

        return Collections.unmodifiableMap(associativeArrays);

    }



    public LinkedHashMap<String, String> getAssociativeArray(String name) {

        return associativeArrays.computeIfAbsent(name, k -> new LinkedHashMap<>());

    }



    public String getAssociativeArrayValue(String name, String key) {

        Map<String, String> map = associativeArrays.get(name);

        return map == null ? "" : map.getOrDefault(key, "");

    }



    public void setAssociativeArrayValue(String name, String key, String value) {

        ensureAssociativeArray(name);

        associativeArrays.get(name).put(key, value);

    }



    public void ensureAssociativeArray(String name) {

        if (!associativeArrays.containsKey(name)) {

            associativeArrays.put(name, new LinkedHashMap<>());

        }

    }



    public void unsetArrayElement(String name, String keyOrIndex) {

        if (isAssociativeArray(name)) {

            associativeArrays.get(name).remove(keyOrIndex);

        } else if (isIndexedArray(name)) {

            try {

                int idx = Integer.parseInt(keyOrIndex);

                indexedArrays.get(name).remove(idx);

            } catch (NumberFormatException ignored) {}

        }

    }



    public void removeArrayElement(String name, String keyOrIndex) {

        unsetArrayElement(name, keyOrIndex);

    }



    public void setIndexedArray(String name, List<String> values) {

        ensureIndexedArray(name);

        TreeMap<Integer, String> map = indexedArrays.get(name);

        map.clear();

        if (values != null) {

            for (int i = 0; i < values.size(); i++) {

                map.put(i, values.get(i));

            }

        }

    }



    public void setAssociativeArray(String name, Map<String, String> values) {

        ensureAssociativeArray(name);

        LinkedHashMap<String, String> map = associativeArrays.get(name);

        map.clear();

        if (values != null) {

            map.putAll(values);

        }

    }



    public void unsetVariable(String name) {

        if (name.contains("[") && name.endsWith("]")) {

            int b = name.indexOf('[');

            String arr = name.substring(0, b);

            String key = name.substring(b + 1, name.length() - 1);

            unsetArrayElement(arr, key);

        } else {

            removeVariable(name);

            indexedArrays.remove(name);

            associativeArrays.remove(name);

        }

    }



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



    public void setVariable(String name, String value, boolean export) {

        if (readonlyVariables.contains(name)) {

            standardError.println("omsh: " + name + ": readonly variable");

            return;

        }

        variables.put(name, value);

        if (export) {

            exportedVariables.add(name);

        }

    }



    public String getVariable(String name) {

        return variables.get(name);

    }



    public boolean hasVariable(String name) {

        return variables.containsKey(name) || indexedArrays.containsKey(name) || associativeArrays.containsKey(name);

    }



    public void removeVariable(String name) {

        if (!readonlyVariables.contains(name)) {

            variables.remove(name);

            exportedVariables.remove(name);

        }

    }



    public Map<String, String> getVariables() {

        return Collections.unmodifiableMap(variables);

    }



    public Map<String, String> getMutableVariables() {

        return variables;

    }



    public boolean isExported(String name) {

        return exportedVariables.contains(name);

    }



    public Set<String> getExportedVariables() {

        return exportedVariables;

    }



    public Shell createSubshell() {

        Shell child = new Shell();

        child.setWorkingDirectory(this.workingDirectory);

        child.variables.putAll(this.variables);

        child.exportedVariables.addAll(this.exportedVariables);

        for (Map.Entry<String, TreeMap<Integer, String>> e : this.indexedArrays.entrySet()) {

            child.getIndexedArray(e.getKey()).putAll(e.getValue());

        }

        for (Map.Entry<String, LinkedHashMap<String, String>> e : this.associativeArrays.entrySet()) {

            child.getAssociativeArray(e.getKey()).putAll(e.getValue());

        }

        child.setPositionalParameters(new ArrayList<>(this.positionalParameters));

        child.setScriptName(this.scriptName);

        return child;

    }



    public void setExported(String name) {

        exportedVariables.add(name);

    }



    public void setReadonly(String name) {

        readonlyVariables.add(name);

    }



    public boolean isReadonly(String name) {

        return readonlyVariables.contains(name);

    }



    public void setIntegerAttribute(String name) {

        integerVariables.add(name);

    }



    public boolean isInteger(String name) {

        return integerVariables.contains(name);

    }



    public void setOption(String option, boolean enabled) {

        shellOptions.put(option.toLowerCase(), enabled);

    }



    public boolean isOptionEnabled(String option) {

        return Boolean.TRUE.equals(shellOptions.get(option.toLowerCase()));

    }



    public Map<String, Boolean> getOptions() {

        return Collections.unmodifiableMap(shellOptions);

    }



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



    public void registerFunction(String name, List<String> bodyLines) {

        functions.put(name, new ArrayList<>(bodyLines));

    }



    public boolean hasFunction(String name) {

        return functions.containsKey(name);

    }



    public List<String> getFunction(String name) {

        return functions.get(name);

    }



    public void registerTrap(String signal, String command) {

        String sig = SignalManager.normalizeSignalName(signal);

        if (command == null || command.isEmpty() || command.equals("-")) {

            traps.remove(sig);

            if (signalManager != null) {

                signalManager.restorePreviousHandler(sig);

            }

        } else {

            traps.put(sig, command);

        }

    }



    public String getTrap(String signal) {

        return traps.get(SignalManager.normalizeSignalName(signal));

    }



    public Map<String, String> getAllTraps() {

        return Collections.unmodifiableMap(traps);

    }



    public Map<String, String> getTraps() {

        return traps;

    }



    public void removeTrap(String signal) {

        registerTrap(signal, null);

    }



    public void triggerTrap(String signal) {

        String sig = SignalManager.normalizeSignalName(signal);

        String cmd = traps.get(sig);

        if (cmd != null && !cmd.isEmpty()) {

            try {

                executeLine(cmd);

            } catch (Exception ignored) {}

        }

    }



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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            triggerTrap("EXIT");

            if (signalManager != null) {

                signalManager.close();

            }

        }, "om-exit-hook"));

    }



    public void shift(int n) {

        if (n <= 0) return;

        int shiftCount = Math.min(n, positionalParameters.size());

        positionalParameters = new ArrayList<>(positionalParameters.subList(shiftCount, positionalParameters.size()));

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



    public SignalManager getSignalManager() {

        return signalManager;

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



    private String unquoteAssignmentValue(String value) {

        if (value.length() >= 2) {

            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {

                return value.substring(1, value.length() - 1);

            }

        }

        return value;

    }

}
