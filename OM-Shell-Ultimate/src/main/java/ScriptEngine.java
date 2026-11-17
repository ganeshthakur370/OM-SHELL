
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Executes shell script files and control flow statements (if/elif/else/fi,
 * for/done, while/done, until/done, functions, and variable assignments).
 */
public class ScriptEngine {

    private static final Pattern FUNC_DEF_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_-]*)\\s*\\(\\)\\s*\\{?\\s*$");

    private final Shell shell;

    public ScriptEngine(Shell shell) {
        this.shell = shell;
    }

    /**
     * Executes a script file from disk with optional positional arguments.
     *
     * @param scriptPath Path to script file
     * @param args       Positional arguments for the script ($1, $2, ...)
     * @return Exit status of the script
     * @throws IOException If the file cannot be read
     */
    public int executeFile(Path scriptPath, String[] args) throws IOException {
        if (!Files.exists(scriptPath)) {
            shell.getStandardError().println("omsh: " + scriptPath + ": No such file or directory");
            return 127;
        }

        List<String> rawLines = Files.readAllLines(scriptPath);
        return executeScriptSource(String.join("\n", rawLines), scriptPath.toString(), args);
    }

    /**
     * Executes script source code with positional arguments.
     */
    public int executeScriptSource(String source, String scriptName, String[] args) {
        List<String> oldPositional = shell.getPositionalParameters();
        String oldScriptName = shell.getScriptName();

        if (scriptName != null) {
            shell.setScriptName(scriptName);
        }
        if (args != null) {
            shell.setPositionalParameters(List.of(args));
        }

        int lastStatus = 0;
        try {
            List<String> logicalLines = splitIntoLogicalLines(source);
            LineCursor cursor = new LineCursor(logicalLines);

            while (cursor.hasNext()) {
                String line = cursor.next().trim();

                // Skip comments and blank lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                lastStatus = executeBlockOrStatement(line, cursor);
            }
        } finally {
            shell.setPositionalParameters(oldPositional);
            shell.setScriptName(oldScriptName);
        }

        return lastStatus;
    }

    /**
     * Checks if a line contains compound block constructs.
     */
    public static boolean containsBlockConstruct(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        return trimmed.startsWith("if ") || trimmed.equals("if")
                || trimmed.startsWith("for ") || trimmed.equals("for")
                || trimmed.startsWith("while ") || trimmed.equals("while")
                || trimmed.startsWith("until ") || trimmed.equals("until")
                || FUNC_DEF_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Executes a single statement or initiates block execution.
     */
    public int executeBlockOrStatement(String line, LineCursor cursor) {
        // 1. Function definition
        Matcher funcMatcher = FUNC_DEF_PATTERN.matcher(line);
        if (funcMatcher.matches()) {
            String funcName = funcMatcher.group(1);
            List<String> body = collectBlockUntil(cursor, "}");
            shell.registerFunction(funcName, body);
            return 0;
        }

        // 2. if / then / elif / else / fi
        if (line.startsWith("if ") || line.equals("if")) {
            return executeIfBlock(line, cursor);
        }

        // 3. for <var> in <items>; do ... done
        if (line.startsWith("for ") || line.equals("for")) {
            return executeForBlock(line, cursor);
        }

        // 4. while <condition>; do ... done
        if (line.startsWith("while ") || line.equals("while")) {
            return executeWhileBlock(line, cursor, false);
        }

        // 5. until <condition>; do ... done
        if (line.startsWith("until ") || line.equals("until")) {
            return executeWhileBlock(line, cursor, true);
        }

        // 6. Normal shell line
        return shell.executeLine(line);
    }

    private int executeIfBlock(String initialLine, LineCursor cursor) {
        List<Branch> branches = new ArrayList<>();
        List<String> elseBody = new ArrayList<>();

        String currentCondition = extractCondition(initialLine, "if");
        List<String> currentBody = new ArrayList<>();
        boolean insideElse = false;

        while (cursor.hasNext()) {
            String line = cursor.next().trim();

            if (line.equals("fi") || line.startsWith("fi ") || line.startsWith("fi;")) {
                break;
            }

            if (line.startsWith("elif ") || line.equals("elif")) {
                if (!insideElse) {
                    branches.add(new Branch(currentCondition, new ArrayList<>(currentBody)));
                    currentBody.clear();
                    currentCondition = extractCondition(line, "elif");
                }
                continue;
            }

            if (line.equals("else") || line.startsWith("else ") || line.startsWith("else;")) {
                if (!insideElse) {
                    branches.add(new Branch(currentCondition, new ArrayList<>(currentBody)));
                    currentBody.clear();
                    insideElse = true;
                    if (line.startsWith("else ")) {
                        String remainder = line.substring(5).trim();
                        if (!remainder.isEmpty()) elseBody.add(remainder);
                    }
                }
                continue;
            }

            if (line.equals("then") || line.startsWith("then ")) {
                if (line.startsWith("then ")) {
                    String remainder = line.substring(5).trim();
                    if (!remainder.isEmpty()) currentBody.add(remainder);
                }
                continue;
            }

            if (insideElse) {
                elseBody.add(line);
            } else {
                currentBody.add(line);
            }
        }

        if (!insideElse) {
            branches.add(new Branch(currentCondition, currentBody));
        }

        // Evaluate branches in order
        for (Branch branch : branches) {
            int condStatus = shell.executeLine(branch.condition);
            if (condStatus == 0) {
                return executeStatementList(branch.body);
            }
        }

        if (insideElse && !elseBody.isEmpty()) {
            return executeStatementList(elseBody);
        }

        return 0;
    }

    private int executeForBlock(String initialLine, LineCursor cursor) {
        String header = initialLine;
        if (header.endsWith(";")) header = header.substring(0, header.length() - 1);
        if (header.endsWith("; do")) header = header.substring(0, header.length() - 4);
        if (header.endsWith(" do")) header = header.substring(0, header.length() - 3);

        String rest = header.substring(4).trim(); // remove "for "
        int inIdx = rest.indexOf(" in ");
        if (inIdx == -1) {
            shell.getStandardError().println("omsh: syntax error in for loop");
            return 2;
        }

        String varName = rest.substring(0, inIdx).trim();
        String itemsStr = rest.substring(inIdx + 4).trim();

        List<String> body = collectBlockUntil(cursor, "done");

        // Expand items expression
        String expandedItems = shell.expand(itemsStr);
        String[] items = expandedItems.trim().split("\\s+");

        int lastStatus = 0;
        for (String item : items) {
            if (item.isEmpty()) continue;
            shell.setVariable(varName, item, false);
            lastStatus = executeStatementList(body);
        }

        return lastStatus;
    }

    private int executeWhileBlock(String initialLine, LineCursor cursor, boolean until) {
        String keyword = until ? "until" : "while";
        String condition = extractCondition(initialLine, keyword);

        List<String> body = collectBlockUntil(cursor, "done");

        int lastStatus = 0;
        int maxLoopIterations = 100000;
        int iteration = 0;

        while (iteration++ < maxLoopIterations) {
            int condStatus = shell.executeLine(condition);
            boolean shouldRun = until ? (condStatus != 0) : (condStatus == 0);
            if (!shouldRun) {
                break;
            }
            lastStatus = executeStatementList(body);
        }

        return lastStatus;
    }

    private int executeStatementList(List<String> lines) {
        int status = 0;
        LineCursor subCursor = new LineCursor(lines);
        while (subCursor.hasNext()) {
            String line = subCursor.next().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            status = executeBlockOrStatement(line, subCursor);
        }
        return status;
    }

    private String extractCondition(String line, String keyword) {
        String content = line.substring(keyword.length()).trim();
        if (content.endsWith("; do")) {
            content = content.substring(0, content.length() - 4).trim();
        } else if (content.endsWith(" do")) {
            content = content.substring(0, content.length() - 3).trim();
        } else if (content.endsWith("; then")) {
            content = content.substring(0, content.length() - 6).trim();
        } else if (content.endsWith(" then")) {
            content = content.substring(0, content.length() - 5).trim();
        } else if (content.endsWith(";")) {
            content = content.substring(0, content.length() - 1).trim();
        }
        return content;
    }

    private List<String> collectBlockUntil(LineCursor cursor, String terminator) {
        List<String> block = new ArrayList<>();
        int depth = 1;

        while (cursor.hasNext()) {
            String line = cursor.next().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (terminator.equals("done")) {
                if (line.startsWith("for ") || line.startsWith("while ") || line.startsWith("until ")) {
                    depth++;
                } else if (line.equals("done") || line.startsWith("done ") || line.startsWith("done;")) {
                    depth--;
                    if (depth == 0) break;
                }
            } else if (terminator.equals("}")) {
                if (line.contains("{")) {
                    depth++;
                }
                if (line.equals("}") || line.startsWith("} ")) {
                    depth--;
                    if (depth == 0) break;
                }
            }

            if (line.equals("do") || line.startsWith("do ")) {
                if (line.startsWith("do ")) {
                    String remainder = line.substring(3).trim();
                    if (!remainder.isEmpty()) block.add(remainder);
                }
                continue;
            }

            block.add(line);
        }

        return block;
    }

    /**
     * Splits a shell script into logical lines, handling semicolons outside quotes/parentheses,
     * backslash line continuations, and newlines.
     */
    public static List<String> splitIntoLogicalLines(String script) {
        List<String> result = new ArrayList<>();
        if (script == null) return result;

        StringBuilder current = new StringBuilder();
        char activeQuote = 0;
        boolean escaped = false;
        int parenDepth = 0;

        for (int i = 0; i < script.length(); i++) {
            char ch = script.charAt(i);

            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }

            if (ch == '\\' && activeQuote != '\'') {
                // Check if backslash is at end of line (line continuation)
                if (i + 1 < script.length() && (script.charAt(i + 1) == '\n' || script.charAt(i + 1) == '\r')) {
                    i++;
                    if (i < script.length() && script.charAt(i) == '\r' && i + 1 < script.length() && script.charAt(i + 1) == '\n') {
                        i++;
                    }
                    continue;
                }
                escaped = true;
                current.append(ch);
                continue;
            }

            if (activeQuote == '\'') {
                if (ch == '\'') activeQuote = 0;
                current.append(ch);
                continue;
            }

            if (activeQuote == '"') {
                if (ch == '"') activeQuote = 0;
                current.append(ch);
                continue;
            }

            if (ch == '\'' || ch == '"') {
                activeQuote = ch;
                current.append(ch);
                continue;
            }

            if (ch == '(') {
                parenDepth++;
                current.append(ch);
                continue;
            }

            if (ch == ')') {
                if (parenDepth > 0) parenDepth--;
                current.append(ch);
                continue;
            }

            if (parenDepth > 0) {
                current.append(ch);
                continue;
            }

            // Outside quotes and subshells: split on ';' or '\n'
            if (ch == ';' || ch == '\n' || ch == '\r') {
                String line = current.toString().trim();
                if (!line.isEmpty()) {
                    result.add(line);
                }
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            result.add(remaining);
        }

        return result;
    }

    public static class LineCursor {
        private final List<String> lines;
        private int index = 0;

        public LineCursor(List<String> lines) {
            this.lines = lines;
        }

        public boolean hasNext() {
            return index < lines.size();
        }

        public String next() {
            return lines.get(index++);
        }

        public String peek() {
            return hasNext() ? lines.get(index) : null;
        }
    }

    private static class Branch {
        final String condition;
        final List<String> body;

        Branch(String condition, List<String> body) {
            this.condition = condition;
            this.body = body;
        }
    }
}
