import java.util.Map;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



public class ArithmeticEvaluator {



    private final String expression;

    private final Map<String, String> variables;

    private int pos = 0;

    private int ch;



    public ArithmeticEvaluator(String expression, Map<String, String> variables) {

        this.expression = expression != null ? expression.trim() : "";

        this.variables = variables;

    }



    public static long evaluate(String expression, Map<String, String> variables) {

        if (expression == null || expression.trim().isEmpty()) {

            return 0;

        }



        String expr = expression.trim();



        if (expr.contains(",")) {

            String[] parts = expr.split(",");

            long last = 0;

            for (String p : parts) {

                if (!p.trim().isEmpty()) {

                    last = evaluate(p.trim(), variables);

                }

            }

            return last;

        }



        Matcher assignMatcher = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(\\+=|-=|\\*=|/=|%=|=(?!=))\\s*(.*)$").matcher(expr);

        if (assignMatcher.matches()) {

            String varName = assignMatcher.group(1);

            String op = assignMatcher.group(2);

            String rhs = assignMatcher.group(3);



            long rhsVal = evaluate(rhs, variables);

            long finalVal;



            if (op.equals("=")) {

                finalVal = rhsVal;

            } else {

                long currentVal = parseLongSafe(variables != null ? variables.get(varName) : null);

                finalVal = switch (op) {

                    case "+=" -> currentVal + rhsVal;

                    case "-=" -> currentVal - rhsVal;

                    case "*=" -> currentVal * rhsVal;

                    case "/=" -> rhsVal != 0 ? currentVal / rhsVal : 0;

                    case "%=" -> rhsVal != 0 ? currentVal % rhsVal : 0;

                    default -> rhsVal;

                };

            }



            if (variables != null) {

                variables.put(varName, String.valueOf(finalVal));

            }

            return finalVal;

        }



        String resolved = resolveVariablesInExpression(expr, variables);

        ArithmeticEvaluator evaluator = new ArithmeticEvaluator(resolved, variables);

        return evaluator.parseTernary();

    }



    private static String resolveVariablesInExpression(String expr, Map<String, String> vars) {

        if (vars == null || expr == null) return expr;

        Pattern idPattern = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\b");

        Matcher m = idPattern.matcher(expr);

        StringBuilder sb = new StringBuilder();

        while (m.find()) {

            String name = m.group(1);

            String val = vars.getOrDefault(name, "0");

            try {

                Long.parseLong(val.trim());

                m.appendReplacement(sb, val.trim());

            } catch (NumberFormatException e) {

                m.appendReplacement(sb, "0");

            }

        }

        m.appendTail(sb);

        return sb.toString();

    }



    private static long parseLongSafe(String s) {

        if (s == null) return 0;

        try {

            return Long.parseLong(s.trim());

        } catch (NumberFormatException e) {

            return 0;

        }

    }



    private void nextChar() {

        ch = (++pos < expression.length()) ? expression.charAt(pos) : -1;

    }



    private boolean eat(int charToEat) {

        while (ch == ' ' || ch == '\t') nextChar();

        if (ch == charToEat) {

            nextChar();

            return true;

        }

        return false;

    }



    public long parseTernary() {

        pos = -1;

        nextChar();

        long result = parseConditionalOr();



        while (ch == ' ' || ch == '\t') nextChar();

        if (eat('?')) {

            long trueVal = parseConditionalOr();

            if (!eat(':')) {

                throw new IllegalArgumentException("Expected ':' in ternary expression");

            }

            long falseVal = parseConditionalOr();

            result = (result != 0) ? trueVal : falseVal;

        }



        return result;

    }



    private long parseConditionalOr() {

        long x = parseConditionalAnd();

        while (true) {

            while (ch == ' ' || ch == '\t') nextChar();

            if (ch == '|' && pos + 1 < expression.length() && expression.charAt(pos + 1) == '|') {

                nextChar();

                nextChar();

                long y = parseConditionalAnd();

                x = (x != 0 || y != 0) ? 1 : 0;

            } else {

                return x;

            }

        }

    }



    private long parseConditionalAnd() {

        long x = parseBitwiseOr();

        while (true) {

            while (ch == ' ' || ch == '\t') nextChar();

            if (ch == '&' && pos + 1 < expression.length() && expression.charAt(pos + 1) == '&') {

                nextChar();

                nextChar();

                long y = parseBitwiseOr();

                x = (x != 0 && y != 0) ? 1 : 0;

            } else {

                return x;

            }

        }

    }



    private long parseBitwiseOr() {

        long x = parseBitwiseXor();

        while (true) {

            while (ch == ' ' || ch == '\t') nextChar();

            if (ch == '|' && (pos + 1 >= expression.length() || expression.charAt(pos + 1) != '|')) {

                nextChar();

                x |= parseBitwiseXor();

            } else {

                return x;

            }

        }

    }



    private long parseBitwiseXor() {

        long x = parseBitwiseAnd();

        while (true) {

            while (ch == ' ' || ch == '\t') nextChar();

            if (ch == '^') {

                nextChar();

                x ^= parseBitwiseAnd();

            } else {

                return x;

            }

        }

    }



    private long parseBitwiseAnd() {

        long x = parseComparison();

        while (true) {

            while (ch == ' ' || ch == '\t') nextChar();

            if (ch == '&' && (pos + 1 >= expression.length() || expression.charAt(pos + 1) != '&')) {

                nextChar();

                x &= parseComparison();

            } else {

                return x;

            }

        }

    }



    private long parseComparison() {

        long x = parseShift();

        while (true) {

            while (ch == ' ' || ch == '\t') nextChar();

            if (ch == '<') {

                nextChar();

                if (ch == '=') {

                    nextChar();

                    x = (x <= parseShift()) ? 1 : 0;

                } else if (ch == '<') {



                    return x;

                } else {

                    x = (x < parseShift()) ? 1 : 0;

                }

            } else if (ch == '>') {

                nextChar();

                if (ch == '=') {

                    nextChar();

                    x = (x >= parseShift()) ? 1 : 0;

                } else if (ch == '>') {



                    return x;

                } else {

                    x = (x > parseShift()) ? 1 : 0;

                }

            } else if (ch == '=' && pos + 1 < expression.length() && expression.charAt(pos + 1) == '=') {

                nextChar();

                nextChar();

                x = (x == parseShift()) ? 1 : 0;

            } else if (ch == '!' && pos + 1 < expression.length() && expression.charAt(pos + 1) == '=') {

                nextChar();

                nextChar();

                x = (x != parseShift()) ? 1 : 0;

            } else {

                return x;

            }

        }

    }



    private long parseShift() {

        long x = parseAddSub();

        while (true) {

            while (ch == ' ' || ch == '\t') nextChar();

            if (ch == '<' && pos + 1 < expression.length() && expression.charAt(pos + 1) == '<') {

                nextChar();

                nextChar();

                x <<= parseAddSub();

            } else if (ch == '>' && pos + 1 < expression.length() && expression.charAt(pos + 1) == '>') {

                nextChar();

                nextChar();

                x >>= parseAddSub();

            } else {

                return x;

            }

        }

    }



    private long parseAddSub() {

        long x = parseMulDiv();

        while (true) {

            if (eat('+')) x += parseMulDiv();

            else if (eat('-')) x -= parseMulDiv();

            else return x;

        }

    }



    private long parseMulDiv() {

        long x = parseFactor();

        while (true) {

            if (eat('*')) x *= parseFactor();

            else if (eat('/')) {

                long divisor = parseFactor();

                if (divisor == 0) throw new ArithmeticException("division by zero");

                x /= divisor;

            } else if (eat('%')) {

                long divisor = parseFactor();

                if (divisor == 0) throw new ArithmeticException("division by zero");

                x %= divisor;

            } else return x;

        }

    }



    private long parseFactor() {

        if (eat('+')) return +parseFactor();

        if (eat('-')) return -parseFactor();

        if (eat('~')) return ~parseFactor();

        if (eat('!')) return (parseFactor() == 0) ? 1 : 0;



        long x;

        int startPos = this.pos;

        if (eat('(')) {

            x = parseConditionalOr();

            eat(')');

        } else if ((ch >= '0' && ch <= '9')) {

            while ((ch >= '0' && ch <= '9')) nextChar();

            x = Long.parseLong(expression.substring(startPos, this.pos));

        } else {

            throw new IllegalArgumentException("Unexpected character in arithmetic expression: '" + (char) ch + "'");

        }



        return x;

    }

}
