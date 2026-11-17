import java.util.List;

import java.util.Map;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



public class Expander {



    private static final int MAX_ALIAS_DEPTH = 10;

    private static final int MAX_SUBSTITUTION_DEPTH = 20;



    

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



            if (aliasReplacement.equals(firstWord)) {

                break;

            }



            String remainder = (firstSpace == -1) ? "" : trimmed.substring(firstSpace);

            result = aliasReplacement + remainder;

        }



        return result;

    }



    

    public static String expandCommandSubstitution(String input, Shell shell) {

        if (input == null || (!input.contains("$(") && !input.contains("`"))) {

            return input;

        }



        String result = input;

        int depth = 0;



        int searchFrom = 0;

        while (searchFrom < result.length() && depth++ < MAX_SUBSTITUTION_DEPTH) {

            int startIdx = result.indexOf("$(", searchFrom);

            if (startIdx == -1) {

                break;

            }



            if (result.startsWith("$((", startIdx)) {

                searchFrom = startIdx + 3;

                continue;

            }



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

                break;

            }



            String subCommand = result.substring(startIdx + 2, endIdx);

            String output = shell.executeSubshell(subCommand);



            output = stripTrailingNewlines(output);



            result = result.substring(0, startIdx) + output + result.substring(endIdx + 1);

            searchFrom = startIdx + output.length();

        }



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



            String expandedExpr = expandVariables(expr, shell);



            long value = ArithmeticEvaluator.evaluate(expandedExpr, shell.getVariables());

            result = result.substring(0, startIdx) + value + result.substring(endIdx + 2);

        }



        return result;

    }



    

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



            if (ch == '$' && !inSingleQuote && i + 1 < length) {

                char next = input.charAt(i + 1);



                if (next == '\'') {

                    int close = input.indexOf('\'', i + 2);

                    if (close != -1) {

                        String inner = input.substring(i + 2, close);

                        output.append(Lexer.decodeAnsiC(inner));

                        i = close;

                        continue;

                    }

                }



                if (next == '?') {

                    output.append(shell.getLastExitCode());

                    i++;

                    continue;

                }



                if (next == '$') {

                    output.append(ProcessHandle.current().pid());

                    i++;

                    continue;

                }



                if (next == '!') {

                    output.append(shell.getLastBackgroundPid());

                    i++;

                    continue;

                }



                if (next == '#') {

                    output.append(shell.getPositionalParameterCount());

                    i++;

                    continue;

                }



                if (Character.isDigit(next)) {

                    int paramIndex = next - '0';

                    output.append(shell.getPositionalParameter(paramIndex));

                    i++;

                    continue;

                }



                if (next == '{') {

                    int closeIdx = findClosingBrace(input, i + 2);

                    if (closeIdx != -1) {

                        String expr = input.substring(i + 2, closeIdx).trim();

                        String resolved = ParameterExpander.expand(expr, shell);

                        output.append(resolved);

                        i = closeIdx;

                        continue;

                    }

                }



                if (isVariableStartChar(next)) {

                    int start = i + 1;

                    int end = start;

                    while (end < length && isVariablePartChar(input.charAt(end))) end++;

                    if (end < length && input.charAt(end) == '[') {

                        int close = input.indexOf(']', end + 1);

                        if (close > end) end = close + 1;

                    }

                    String varName = input.substring(start, end);

                    output.append(ParameterExpander.expand(varName, shell));

                    i = end - 1;

                    continue;

                }

            }



            output.append(ch);

        }



        return output.toString();

    }



    private static int findClosingBrace(String input, int start) {

        int depth = 1;

        for (int i = start; i < input.length(); i++) {

            char c = input.charAt(i);

            if (c == '\\') {

                i++;

                continue;

            }

            if (c == '{') {

                depth++;

            } else if (c == '}') {

                depth--;

                if (depth == 0) {

                    return i;

                }

            }

        }

        return -1;

    }



    

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
