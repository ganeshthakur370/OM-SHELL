import java.util.ArrayList;

import java.util.Collections;

import java.util.List;

import java.util.Map;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



public class ParameterExpander {



    public static String expand(String expression, Shell shell) {

        if (expression == null || expression.isEmpty() || shell == null) {

            return "";

        }



        String expr = expression.trim();



        if (expr.startsWith("#") && (expr.endsWith("[@]") || expr.endsWith("[*]"))) {

            String name = expr.substring(1, expr.length() - 3);

            if (shell.isIndexedArray(name)) {

                return String.valueOf(shell.getIndexedArray(name).size());

            }

            if (shell.isAssociativeArray(name)) {

                return String.valueOf(shell.getAssociativeArray(name).size());

            }

            return "0";

        }



        if (expr.startsWith("#") && isIdentStart(expr.charAt(1))) {

            String varName = expr.substring(1);

            String val = resolveSimpleVariable(varName, shell);

            return String.valueOf(val.length());

        }



        if (expr.startsWith("!")) {

            String sub = expr.substring(1);

            if (sub.endsWith("[@]") || sub.endsWith("[*]")) {

                String name = sub.substring(0, sub.length() - 3);

                if (shell.isAssociativeArray(name)) {

                    return String.join(" ", shell.getAssociativeArray(name).keySet());

                }

                if (shell.isIndexedArray(name)) {

                    List<String> indices = new ArrayList<>();

                    for (Integer k : shell.getIndexedArray(name).keySet()) {

                        indices.add(String.valueOf(k));

                    }

                    return String.join(" ", indices);

                }

                return "";

            } else {



                String indirectTarget = resolveSimpleVariable(sub, shell);

                if (!indirectTarget.isEmpty()) {

                    return resolveSimpleVariable(indirectTarget, shell);

                }

                return "";

            }

        }



        if ((expr.startsWith("${") || true) && (expr.contains("[@]:") || expr.contains("[*]:"))) {

            int bracketColon = expr.indexOf("]:");

            if (bracketColon > 0) {

                String arrayRef = expr.substring(0, bracketColon + 1);

                String sliceArgs = expr.substring(bracketColon + 2);

                if (arrayRef.endsWith("[@]") || arrayRef.endsWith("[*]")) {

                    String arrName = arrayRef.substring(0, arrayRef.length() - 3);

                    return sliceArray(arrName, sliceArgs, shell);

                }

            }

        }



        int bracketOpen = expr.indexOf('[');

        if (bracketOpen > 0 && expr.endsWith("]")) {

            String name = expr.substring(0, bracketOpen);

            String key = expr.substring(bracketOpen + 1, expr.length() - 1);

            return resolveArrayElement(name, key, shell);

        }



        if (expr.contains("#") || expr.contains("%")) {

            String res = expandPrefixSuffixRemoval(expr, shell);

            if (res != null) return res;

        }



        int slashIdx = expr.indexOf('/');

        if (slashIdx > 0 && isIdent(expr.substring(0, slashIdx))) {

            return expandPatternReplacement(expr, slashIdx, shell);

        }



        if (expr.contains("^") || expr.contains(",")) {

            String res = expandCaseModification(expr, shell);

            if (res != null) return res;

        }



        String res = expandDefaultOrAlternate(expr, shell);

        if (res != null) return res;



        int colonIdx = expr.indexOf(':');

        if (colonIdx > 0 && isIdent(expr.substring(0, colonIdx))) {

            return expandSubstring(expr, colonIdx, shell);

        }



        return resolveSimpleVariable(expr, shell);

    }



    private static String resolveArrayElement(String name, String indexExpr, Shell shell) {

        if (indexExpr.equals("@") || indexExpr.equals("*")) {

            if (shell.isIndexedArray(name)) {

                return String.join(" ", shell.getIndexedArrayValues(name));

            }

            if (shell.isAssociativeArray(name)) {

                return String.join(" ", shell.getAssociativeArray(name).values());

            }

            return "";

        }



        if (shell.isIndexedArray(name)) {

            try {

                int idx = (int) ArithmeticEvaluator.evaluate(indexExpr, shell.getVariables());

                return shell.getIndexedArrayValue(name, idx);

            } catch (Exception e) {

                return "";

            }

        }



        if (shell.isAssociativeArray(name)) {

            return shell.getAssociativeArrayValue(name, indexExpr);

        }



        if (indexExpr.equals("0")) {

            return shell.getVariable(name) != null ? shell.getVariable(name) : "";

        }



        return "";

    }



    private static String sliceArray(String arrName, String sliceArgs, Shell shell) {

        List<String> values = shell.getIndexedArrayValues(arrName);

        if (values.isEmpty()) return "";



        String[] parts = sliceArgs.split(":", 2);

        int offset = 0;

        int length = values.size();



        try {

            offset = (int) ArithmeticEvaluator.evaluate(parts[0], shell.getVariables());

            if (offset < 0) offset = Math.max(0, values.size() + offset);

            if (offset >= values.size()) return "";



            if (parts.length > 1) {

                length = (int) ArithmeticEvaluator.evaluate(parts[1], shell.getVariables());

                if (length < 0) {

                    length = Math.max(0, values.size() + length - offset);

                }

            }



            int end = Math.min(values.size(), offset + length);

            if (offset >= end) return "";



            return String.join(" ", values.subList(offset, end));

        } catch (Exception e) {

            return String.join(" ", values);

        }

    }



    private static String expandPatternReplacement(String expr, int firstSlash, Shell shell) {

        String varName = expr.substring(0, firstSlash);

        String val = resolveSimpleVariable(varName, shell);

        String remainder = expr.substring(firstSlash + 1);



        boolean replaceAll = false;

        boolean anchorStart = false;

        boolean anchorEnd = false;



        if (remainder.startsWith("/")) {

            replaceAll = true;

            remainder = remainder.substring(1);

        } else if (remainder.startsWith("#")) {

            anchorStart = true;

            remainder = remainder.substring(1);

        } else if (remainder.startsWith("%")) {

            anchorEnd = true;

            remainder = remainder.substring(1);

        }



        String patternStr;

        String replacement = "";

        int secondSlash = remainder.indexOf('/');

        if (secondSlash != -1) {

            patternStr = remainder.substring(0, secondSlash);

            replacement = remainder.substring(secondSlash + 1);

        } else {

            patternStr = remainder;

        }



        if (patternStr.isEmpty()) return val;



        String regex = globToRegex(patternStr);

        if (anchorStart) regex = "^" + regex;

        if (anchorEnd) regex = regex + "$";



        try {

            Pattern p = Pattern.compile(regex);

            Matcher m = p.matcher(val);

            if (replaceAll) {

                return m.replaceAll(Matcher.quoteReplacement(replacement));

            } else {

                return m.replaceFirst(Matcher.quoteReplacement(replacement));

            }

        } catch (Exception e) {

            return val;

        }

    }



    private static String expandCaseModification(String expr, Shell shell) {

        int opIdx = -1;

        char op = 0;

        int count = 0;



        for (int i = 0; i < expr.length(); i++) {

            char c = expr.charAt(i);

            if (c == '^' || c == ',') {

                opIdx = i;

                op = c;

                count = (i + 1 < expr.length() && expr.charAt(i + 1) == c) ? 2 : 1;

                break;

            }

        }



        if (opIdx <= 0) return null;

        String varName = expr.substring(0, opIdx);

        String val = resolveSimpleVariable(varName, shell);

        if (val.isEmpty()) return "";



        if (op == '^') {

            if (count == 1) {

                return Character.toUpperCase(val.charAt(0)) + (val.length() > 1 ? val.substring(1) : "");

            } else {

                return val.toUpperCase();

            }

        } else {

            if (count == 1) {

                return Character.toLowerCase(val.charAt(0)) + (val.length() > 1 ? val.substring(1) : "");

            } else {

                return val.toLowerCase();

            }

        }

    }



    private static String expandPrefixSuffixRemoval(String expr, Shell shell) {



        int doubleHash = expr.indexOf("##");

        int singleHash = (doubleHash != -1) ? -1 : expr.indexOf('#');

        int doublePercent = expr.indexOf("%%");

        int singlePercent = (doublePercent != -1) ? -1 : expr.indexOf('%');



        if (doubleHash != -1 && isIdent(expr.substring(0, doubleHash))) {

            String varName = expr.substring(0, doubleHash);

            String pattern = expr.substring(doubleHash + 2);

            String val = resolveSimpleVariable(varName, shell);

            return removePrefix(val, pattern, true);

        }

        if (singleHash != -1 && singleHash > 0 && isIdent(expr.substring(0, singleHash))) {

            String varName = expr.substring(0, singleHash);

            String pattern = expr.substring(singleHash + 1);

            String val = resolveSimpleVariable(varName, shell);

            return removePrefix(val, pattern, false);

        }

        if (doublePercent != -1 && isIdent(expr.substring(0, doublePercent))) {

            String varName = expr.substring(0, doublePercent);

            String pattern = expr.substring(doublePercent + 2);

            String val = resolveSimpleVariable(varName, shell);

            return removeSuffix(val, pattern, true);

        }

        if (singlePercent != -1 && singlePercent > 0 && isIdent(expr.substring(0, singlePercent))) {

            String varName = expr.substring(0, singlePercent);

            String pattern = expr.substring(singlePercent + 1);

            String val = resolveSimpleVariable(varName, shell);

            return removeSuffix(val, pattern, false);

        }



        return null;

    }



    private static String removePrefix(String text, String glob, boolean longest) {

        String regex = "^" + globToRegex(glob);

        Pattern pattern = Pattern.compile(regex);



        if (longest) {

            for (int len = text.length(); len >= 0; len--) {

                String sub = text.substring(0, len);

                if (pattern.matcher(sub).matches()) {

                    return text.substring(len);

                }

            }

        } else {

            for (int len = 0; len <= text.length(); len++) {

                String sub = text.substring(0, len);

                if (pattern.matcher(sub).matches()) {

                    return text.substring(len);

                }

            }

        }

        return text;

    }



    private static String removeSuffix(String text, String glob, boolean longest) {

        String regex = globToRegex(glob) + "$";

        Pattern pattern = Pattern.compile(regex);



        if (longest) {

            for (int start = 0; start <= text.length(); start++) {

                String sub = text.substring(start);

                if (pattern.matcher(sub).matches()) {

                    return text.substring(0, start);

                }

            }

        } else {

            for (int start = text.length(); start >= 0; start--) {

                String sub = text.substring(start);

                if (pattern.matcher(sub).matches()) {

                    return text.substring(0, start);

                }

            }

        }

        return text;

    }



    private static String expandDefaultOrAlternate(String expr, Shell shell) {



        String[] operators = { ":-", ":=", ":?", ":+", "-", "=", "?", "+" };

        for (String op : operators) {

            int idx = expr.indexOf(op);

            if (idx > 0 && isIdent(expr.substring(0, idx))) {

                String varName = expr.substring(0, idx);

                String word = expr.substring(idx + op.length());

                String expandedWord = shell.expand(word);



                boolean checkNullOrUnset = op.startsWith(":");

                boolean isSet = shell.hasVariable(varName);

                String val = shell.getVariable(varName);

                boolean isNullOrEmpty = !isSet || val == null || val.isEmpty();



                boolean condition = checkNullOrUnset ? isNullOrEmpty : !isSet;



                if (op.equals(":-") || op.equals("-")) {

                    return condition ? expandedWord : val;

                }

                if (op.equals(":=") || op.equals("=")) {

                    if (condition) {

                        shell.setVariable(varName, expandedWord, false);

                        return expandedWord;

                    }

                    return val;

                }

                if (op.equals(":?") || op.equals("?")) {

                    if (condition) {

                        String errMsg = expandedWord.isEmpty() ? "parameter null or not set" : expandedWord;

                        shell.getStandardError().println("omsh: " + varName + ": " + errMsg);

                        shell.setLastExitCode(1);

                        return "";

                    }

                    return val;

                }

                if (op.equals(":+") || op.equals("+")) {

                    return condition ? "" : expandedWord;

                }

            }

        }

        return null;

    }



    private static String expandSubstring(String expr, int colonIdx, Shell shell) {

        String varName = expr.substring(0, colonIdx);

        String val = resolveSimpleVariable(varName, shell);

        String subArgs = expr.substring(colonIdx + 1);



        String[] parts = subArgs.split(":", 2);

        try {

            int offset = (int) ArithmeticEvaluator.evaluate(parts[0], shell.getVariables());

            if (offset < 0) {

                offset = Math.max(0, val.length() + offset);

            }

            if (offset >= val.length()) {

                return "";

            }



            if (parts.length > 1) {

                int length = (int) ArithmeticEvaluator.evaluate(parts[1], shell.getVariables());

                if (length < 0) {

                    int end = Math.max(0, val.length() + length);

                    if (end <= offset) return "";

                    return val.substring(offset, end);

                } else {

                    int end = Math.min(val.length(), offset + length);

                    return val.substring(offset, end);

                }

            } else {

                return val.substring(offset);

            }

        } catch (Exception e) {

            return val;

        }

    }



    private static String resolveSimpleVariable(String name, Shell shell) {

        if (name.equals("?")) return String.valueOf(shell.getLastExitCode());

        if (name.equals("$")) return String.valueOf(ProcessHandle.current().pid());

        if (name.equals("!")) return String.valueOf(shell.getLastBackgroundPid());

        if (name.equals("#")) return String.valueOf(shell.getPositionalParameterCount());



        try {

            int num = Integer.parseInt(name);

            return shell.getPositionalParameter(num);

        } catch (NumberFormatException ignored) {}



        if (shell.isIndexedArray(name)) {

            List<String> list = shell.getIndexedArrayValues(name);

            return list.isEmpty() ? "" : list.get(0);

        }



        if (shell.isAssociativeArray(name)) {

            return String.join(" ", shell.getAssociativeArray(name).values());

        }



        String val = shell.getVariable(name);

        return val != null ? val : "";

    }



    private static String globToRegex(String glob) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < glob.length(); i++) {

            char c = glob.charAt(i);

            switch (c) {

                case '*' -> sb.append(".*");

                case '?' -> sb.append(".");

                case '.' -> sb.append("\\.");

                case '\\' -> sb.append("\\\\");

                case '[' -> sb.append("\\[");

                case ']' -> sb.append("\\]");

                case '(' -> sb.append("\\(");

                case ')' -> sb.append("\\)");

                case '{' -> sb.append("\\{");

                case '}' -> sb.append("\\}");

                case '^' -> sb.append("\\^");

                case '$' -> sb.append("\\$");

                case '|' -> sb.append("\\|");

                case '+' -> sb.append("\\+");

                default -> sb.append(c);

            }

        }

        return sb.toString();

    }



    private static boolean isIdent(String s) {

        if (s == null || s.isEmpty()) return false;

        if (!isIdentStart(s.charAt(0))) return false;

        for (int i = 1; i < s.length(); i++) {

            if (!isIdentPart(s.charAt(i))) return false;

        }

        return true;

    }



    private static boolean isIdentStart(char c) {

        return Character.isLetter(c) || c == '_';

    }



    private static boolean isIdentPart(char c) {

        return Character.isLetterOrDigit(c) || c == '_';

    }

}
