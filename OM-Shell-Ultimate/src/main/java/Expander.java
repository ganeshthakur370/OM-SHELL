
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles all shell expansion phases:
 * 1. Alias expansion
 * 2. Command substitution $(command) and `command`
 * 3. Arithmetic expansion $((expression))
 * 4. Variable parameter expansion $VAR, ${VAR}, $?, $!, $$, $1..$N
 * 5. Tilde expansion (~ -> user.home)
 */
public class Expander {

    private static final int MAX_ALIAS_DEPTH = 10;
    private static final int MAX_SUBSTITUTION_DEPTH = 20;

    /**
     * Expands aliases at the beginning of the command line string.
     */
    public static String expandAliases(String line, Shell shell) {
        if (line == null || line.trim().isEmpty() || shell == null) {
            return line;
        }

        String result = line;
        for (int depth = 0; depth < MAX_ALIAS_DEPTH; depth++) {
            String trimmed = result.trim();
            int firstSpace = -1;
            for (int i = 0; i < trimmed.length(); i++) {
                if (Character.isWhitespace(trimmed.charAt(i))) {
                    firstSpace = i;
                    break;
                }
            }

            String firstWord = (firstSpace == -1) ? trimmed : trimmed.substring(0, firstSpace);
            String aliasReplacement = shell.getAlias(firstWord);

            if (aliasReplacement == null) {
                break;
            }

            // Prevent direct recursion e.g. alias ls='ls -la'
            if (aliasReplacement.equals(firstWord)) {
                break;
            }

            String remainder = (firstSpace == -1) ? "" : trimmed.substring(firstSpace);
            result = aliasReplacement + remainder;
        }

        return result;
    }

    /**
     * Expands command substitutions: $(command) and `command`.
     */
    public static String expandCommandSubstitution(String input, Shell shell) {
        if (input == null || (!input.contains("$(") && !input.contains("`"))) {
            return input;
        }

        String result = input;
        int depth = 0;

        // Process $( ... ) with nested parenthesis awareness
        int searchFrom = 0;
        while (searchFrom < result.length() && depth++ < MAX_SUBSTITUTION_DEPTH) {
            int startIdx = result.indexOf("$(", searchFrom);
            if (startIdx == -1) {
                break;
            }

            // Skip arithmetic expansions $(( ... ))
            if (result.startsWith("$((", startIdx)) {
                searchFrom = startIdx + 3;
                continue;
            }

            // Verify not inside single quotes
            if (isInsideSingleQuotes(result, startIdx)) {
                searchFrom = startIdx + 2;
                continue;
            }

            int parenCount = 1;
            int endIdx = -1;
            for (int i = startIdx + 2; i < result.length(); i++) {
                char c = result.charAt(i);
                if (c == '(') {
                    parenCount++;
                } else if (c == ')') {
                    parenCount--;
                    if (parenCount == 0) {
                        endIdx = i;
                        break;
                    }
                }
            }

            if (endIdx == -1) {
                break; // Unmatched parenthesis
            }

            String subCommand = result.substring(startIdx + 2, endIdx);
            String output = shell.executeSubshell(subCommand);
            // POSIX strips trailing newlines from command substitution
            output = stripTrailingNewlines(output);

            result = result.substring(0, startIdx) + output + result.substring(endIdx + 1);
            searchFrom = startIdx + output.length();
        }

        // Process ` ... ` backtick substitution
        depth = 0;
        while (result.contains("`") && depth++ < MAX_SUBSTITUTION_DEPTH) {
            int firstBacktick = result.indexOf('`');
            if (isInsideSingleQuotes(result, firstBacktick)) {
                break;
            }

            int secondBacktick = -1;
            for (int i = firstBacktick + 1; i < result.length(); i++) {
                if (result.charAt(i) == '`' && (i == 0 || result.charAt(i - 1) != '\\')) {
                    secondBacktick = i;
                    break;
                }
            }

            if (secondBacktick == -1) {
                break;
            }

            String subCommand = result.substring(firstBacktick + 1, secondBacktick).replace("\\`", "`");
            String output = shell.executeSubshell(subCommand);
            output = stripTrailingNewlines(output);

            result = result.substring(0, firstBacktick) + output + result.substring(secondBacktick + 1);
        }

        return result;
    }

    /**
     * Expands arithmetic expressions: $((expression)).
     */
    public static String expandArithmetic(String input, Shell shell) {
        if (input == null || !input.contains("$((")) {
            return input;
        }

        String result = input;
        int depth = 0;

        while (result.contains("$((") && depth++ < MAX_SUBSTITUTION_DEPTH) {
            int startIdx = result.indexOf("$((");
            if (isInsideSingleQuotes(result, startIdx)) {
                break;
            }

            int endIdx = result.indexOf("))", startIdx + 3);
            if (endIdx == -1) {
                break;
            }

            String expr = result.substring(startIdx + 3, endIdx);
            // First expand any variables inside the arithmetic expression
            String expandedExpr = expandVariables(expr, shell);

            long value = ArithmeticEvaluator.evaluate(expandedExpr, shell.getVariables());
            result = result.substring(0, startIdx) + value + result.substring(endIdx + 2);
        }

        return result;
    }

    /**
     * Expands variables: $NAME, ${NAME}, $?, $!, $$, $0..$9, $#, $*, $@.
     * Skips expansion if the variable is inside single quotes.
     */
    public static String expandVariables(String input, Shell shell) {
        if (input == null || !input.contains("$")) {
            return input;
        }

        StringBuilder output = new StringBuilder();
        int length = input.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < length; i++) {
            char ch = input.charAt(i);

            if (escaped) {
                output.append(ch);
                escaped = false;
                continue;
            }

            if (ch == '\\' && !inSingleQuote) {
                escaped = true;
                output.append(ch);
                continue;
            }

            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                output.append(ch);
                continue;
            }

            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                output.append(ch);
                continue;
            }

            // Variable expansion triggered only outside single quotes
            if (ch == '$' && !inSingleQuote && i + 1 < length) {
                char next = input.charAt(i + 1);

                // Special variable: $?
                if (next == '?') {
                    output.append(shell.getLastExitCode());
                    i++;
                    continue;
                }

                // Special variable: $$
                if (next == '$') {
                    output.append(ProcessHandle.current().pid());
                    i++;
                    continue;
                }

                // Special variable: $!
                if (next == '!') {
                    output.append(shell.getLastBackgroundPid());
                    i++;
                    continue;
                }

                // Special variable: $# (positional parameter count)
                if (next == '#') {
                    output.append(shell.getPositionalParameterCount());
                    i++;
                    continue;
                }

                // Positional parameters: $0, $1, etc.
                if (Character.isDigit(next)) {
                    int paramIndex = next - '0';
                    output.append(shell.getPositionalParameter(paramIndex));
                    i++;
                    continue;
                }

                // Braced variable: ${NAME}
                if (next == '{') {
                    int closeIdx = input.indexOf('}', i + 2);
                    if (closeIdx != -1) {
                        String varName = input.substring(i + 2, closeIdx).trim();
                        output.append(resolveParameter(varName, shell));
                        i = closeIdx;
                        continue;
                    }
                }

                // Standard identifier: $NAME and simple indexed lookup: $items[2]
                if (isVariableStartChar(next)) {
                    int start = i + 1;
                    int end = start;
                    while (end < length && isVariablePartChar(input.charAt(end))) end++;
                    if (end < length && input.charAt(end) == '[') {
                        int close = input.indexOf(']', end + 1);
                        if (close > end) end = close + 1;
                    }
                    String varName = input.substring(start, end);
                    output.append(resolveParameter(varName, shell));
                    i = end - 1;
                    continue;
                }
            }

            output.append(ch);
        }

        return output.toString();
    }

    /**
     * Expands tilde ~ to user home directory at word boundaries.
     */
    public static String expandTilde(String word) {
        if (word == null) return null;
        String userHome = System.getProperty("user.home");
        if (word.equals("~")) {
            return userHome;
        }
        if (word.startsWith("~/") || word.startsWith("~\\")) {
            return userHome + word.substring(1);
        }
        return word;
    }


    private static String resolveParameter(String expression, Shell shell) {
        if (expression.startsWith("#") && expression.endsWith("[@]")) {
            String name = expression.substring(1, expression.length() - 3);
            if (shell.isIndexedArray(name)) return String.valueOf(shell.getIndexedArray(name).size());
            if (shell.isAssociativeArray(name)) return String.valueOf(shell.getAssociativeArray(name).size());
        }
        if (expression.startsWith("!") && expression.endsWith("[@]")) {
            String name = expression.substring(1, expression.length() - 3);
            if (shell.isAssociativeArray(name)) return String.join(" ", shell.getAssociativeArray(name).keySet());
        }
        int bracket = expression.indexOf('[');
        if (bracket > 0 && expression.endsWith("]")) {
            String name = expression.substring(0, bracket);
            String index = expression.substring(bracket + 1, expression.length() - 1);
            if (index.equals("@") || index.equals("*")) {
                if (shell.isIndexedArray(name)) return String.join(" ", shell.getIndexedArray(name));
                if (shell.isAssociativeArray(name)) return String.join(" ", shell.getAssociativeArray(name).values());
            }
            if (shell.isIndexedArray(name)) {
                try { return shell.getIndexedArrayValue(name, Integer.parseInt(index)); }
                catch (NumberFormatException ignored) { return ""; }
            }
            if (shell.isAssociativeArray(name)) return shell.getAssociativeArrayValue(name, index);
        }
        if (shell.isIndexedArray(expression)) {
            List<String> values = shell.getIndexedArray(expression);
            return values.isEmpty() ? "" : values.get(0);
        }
        if (shell.isAssociativeArray(expression)) return String.join(" ", shell.getAssociativeArray(expression).values());
        return resolveVariable(expression, shell);
    }

    private static String resolveVariable(String name, Shell shell) {
        if (name.equals("?")) return String.valueOf(shell.getLastExitCode());
        if (name.equals("$")) return String.valueOf(ProcessHandle.current().pid());
        if (name.equals("!")) return String.valueOf(shell.getLastBackgroundPid());
        if (name.equals("#")) return String.valueOf(shell.getPositionalParameterCount());

        try {
            int num = Integer.parseInt(name);
            return shell.getPositionalParameter(num);
        } catch (NumberFormatException ignored) {}

        String val = shell.getVariable(name);
        return val != null ? val : "";
    }

    private static boolean isInsideSingleQuotes(String str, int targetIndex) {
        boolean inside = false;
        for (int i = 0; i < targetIndex && i < str.length(); i++) {
            if (str.charAt(i) == '\'') {
                inside = !inside;
            }
        }
        return inside;
    }

    private static String stripTrailingNewlines(String s) {
        if (s == null) return "";
        int len = s.length();
        while (len > 0 && (s.charAt(len - 1) == '\n' || s.charAt(len - 1) == '\r')) {
            len--;
        }
        return s.substring(0, len);
    }

    private static boolean isVariableStartChar(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isVariablePartChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
