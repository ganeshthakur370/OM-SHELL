import java.io.IOException;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.ArrayList;

import java.util.List;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



public class ScriptEngine {



    private static final Pattern FUNC_DEF_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_-]*)\\s*\\(\\)\\s*\\{?\\s*$");



    private final Shell shell;



    public ScriptEngine(Shell shell) {

        this.shell = shell;

    }



    

    public int executeFile(Path scriptPath, String[] args) throws IOException {

        if (!Files.exists(scriptPath)) {

            shell.getStandardError().println("omsh: " + scriptPath + ": No such file or directory");

            return 127;

        }



        List<String> rawLines = Files.readAllLines(scriptPath);

        return executeScriptSource(String.join("\n", rawLines), scriptPath.toString(), args);

    }



    

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



    

    public static boolean containsBlockConstruct(String line) {

        if (line == null) return false;

        String trimmed = line.trim();

        return trimmed.startsWith("if ") || trimmed.equals("if")

                || trimmed.startsWith("for ") || trimmed.equals("for")

                || trimmed.startsWith("while ") || trimmed.equals("while")

                || trimmed.startsWith("until ") || trimmed.equals("until")

                || trimmed.startsWith("case ") || trimmed.equals("case")

                || FUNC_DEF_PATTERN.matcher(trimmed).matches();

    }



    

    public int executeBlockOrStatement(String line, LineCursor cursor) {



        Matcher funcMatcher = FUNC_DEF_PATTERN.matcher(line);

        if (funcMatcher.matches()) {

            String funcName = funcMatcher.group(1);

            List<String> body = collectBlockUntil(cursor, "}");

            shell.registerFunction(funcName, body);

            return 0;

        }



        if (line.startsWith("if ") || line.equals("if")) {

            return executeIfBlock(line, cursor);

        }



        if (line.startsWith("for ") || line.equals("for")) {

            return executeForBlock(line, cursor);

        }



        if (line.startsWith("while ") || line.equals("while")) {

            return executeWhileBlock(line, cursor, false);

        }



        if (line.startsWith("until ") || line.equals("until")) {

            return executeWhileBlock(line, cursor, true);

        }



        if (line.startsWith("case ") || line.equals("case")) {

            return executeCaseBlock(line, cursor);

        }



        if (line.startsWith("{") && line.endsWith("}")) {

            String inner = line.substring(1, line.length() - 1).trim();

            return executeStatementList(splitIntoLogicalLines(inner));

        }



        if (line.startsWith("(") && line.endsWith(")") && !line.startsWith("((") && !line.contains("()")) {

            String inner = line.substring(1, line.length() - 1).trim();

            Shell subshell = shell.createSubshell();

            return subshell.executeLine(inner);

        }



        if (line.startsWith("((") && line.endsWith("))")) {

            String expr = line.substring(2, line.length() - 2).trim();

            try {

                long result = ArithmeticEvaluator.evaluate(expr, shell.getMutableVariables());

                int status = (result != 0) ? 0 : 1;

                shell.setLastExitCode(status);

                return status;

            } catch (Exception e) {

                shell.getStandardError().println("omsh: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));

                shell.setLastExitCode(1);

                return 1;

            }

        }



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



        String rest = header.substring(4).trim();

        int inIdx = rest.indexOf(" in ");

        if (inIdx == -1) {

            shell.getStandardError().println("omsh: syntax error in for loop");

            return 2;

        }



        String varName = rest.substring(0, inIdx).trim();

        String itemsStr = rest.substring(inIdx + 4).trim();



        List<String> body = collectBlockUntil(cursor, "done");



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



    private int executeCaseBlock(String initialLine, LineCursor cursor) {

        String header = initialLine.substring(4).trim();

        if (header.endsWith(";")) header = header.substring(0, header.length() - 1).trim();

        if (header.endsWith(" in")) header = header.substring(0, header.length() - 3).trim();



        String targetWord = shell.expand(header).trim();

        if (targetWord.startsWith("\"") && targetWord.endsWith("\"") && targetWord.length() >= 2) {

            targetWord = targetWord.substring(1, targetWord.length() - 1);

        } else if (targetWord.startsWith("'") && targetWord.endsWith("'") && targetWord.length() >= 2) {

            targetWord = targetWord.substring(1, targetWord.length() - 1);

        }



        List<CaseClause> clauses = new ArrayList<>();

        List<String> currentPatterns = null;

        List<String> currentBody = new ArrayList<>();



        while (cursor.hasNext()) {

            String line = cursor.next().trim();

            if (line.isEmpty() || line.startsWith("#")) continue;



            if (line.equals("esac") || line.startsWith("esac ") || line.startsWith("esac;")) {

                if (currentPatterns != null) {

                    clauses.add(new CaseClause(currentPatterns, new ArrayList<>(currentBody)));

                }

                break;

            }



            if (line.equals(";;")) {

                if (currentPatterns != null) {

                    clauses.add(new CaseClause(currentPatterns, new ArrayList<>(currentBody)));

                    currentPatterns = null;

                    currentBody.clear();

                }

                continue;

            }



            if (currentPatterns == null && line.contains(")")) {

                int parenIdx = line.indexOf(')');

                String patPart = line.substring(0, parenIdx).trim();

                if (patPart.startsWith("(")) {

                    patPart = patPart.substring(1).trim();

                }

                String[] rawPats = patPart.split("\\|");

                List<String> pats = new ArrayList<>();

                for (String p : rawPats) {

                    String clean = p.trim();

                    if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {

                        clean = clean.substring(1, clean.length() - 1);

                    }

                    pats.add(clean);

                }

                currentPatterns = pats;



                String rest = line.substring(parenIdx + 1).trim();

                if (rest.endsWith(";;")) {

                    rest = rest.substring(0, rest.length() - 2).trim();

                    if (!rest.isEmpty()) currentBody.add(rest);

                    clauses.add(new CaseClause(currentPatterns, new ArrayList<>(currentBody)));

                    currentPatterns = null;

                    currentBody.clear();

                } else if (!rest.isEmpty()) {

                    currentBody.add(rest);

                }

                continue;

            }



            if (line.endsWith(";;")) {

                String cmdPart = line.substring(0, line.length() - 2).trim();

                if (!cmdPart.isEmpty()) currentBody.add(cmdPart);

                if (currentPatterns != null) {

                    clauses.add(new CaseClause(currentPatterns, new ArrayList<>(currentBody)));

                    currentPatterns = null;

                    currentBody.clear();

                }

                continue;

            }



            currentBody.add(line);

        }



        for (CaseClause clause : clauses) {

            boolean matched = false;

            for (String pat : clause.patterns) {

                if (pat.equals("*")) {

                    matched = true;

                    break;

                }

                String regex = TestCommand.globToRegex(pat);

                if (targetWord.matches(regex)) {

                    matched = true;

                    break;

                }

            }

            if (matched) {

                return executeStatementList(clause.body);

            }

        }



        return 0;

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



            if (ch == ';' && i + 1 < script.length() && script.charAt(i + 1) == ';') {

                String line = current.toString().trim();

                if (!line.isEmpty()) {

                    result.add(line);

                }

                result.add(";;");

                current.setLength(0);

                i++;

                continue;

            }



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



    private static class CaseClause {

        final List<String> patterns;

        final List<String> body;



        CaseClause(List<String> patterns, List<String> body) {

            this.patterns = patterns;

            this.body = body;

        }

    }

}
