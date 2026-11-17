
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates POSIX-style integer arithmetic expressions, e.g. for $((expression)).
 * Supports +, -, *, /, %, parenthesis, comparisons, and variable resolution.
 */
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
        // Pre-replace any variable identifiers with their integer values
        String resolved = resolveVariablesInExpression(expression, variables);
        ArithmeticEvaluator evaluator = new ArithmeticEvaluator(resolved, variables);
        return evaluator.parseExpression();
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

    public long parseExpression() {
        pos = -1;
        nextChar();
        long x = parseComparison();
        if (pos < expression.length()) {
            while (ch == ' ' || ch == '\t') nextChar();
            if (pos < expression.length()) {
                throw new IllegalArgumentException("Unexpected character in arithmetic expression: '" + (char) ch + "'");
            }
        }
        return x;
    }

    private long parseComparison() {
        long x = parseAddSub();
        while (true) {
            while (ch == ' ' || ch == '\t') nextChar();
            if (ch == '<') {
                nextChar();
                if (ch == '=') {
                    nextChar();
                    x = (x <= parseAddSub()) ? 1 : 0;
                } else {
                    x = (x < parseAddSub()) ? 1 : 0;
                }
            } else if (ch == '>') {
                nextChar();
                if (ch == '=') {
                    nextChar();
                    x = (x >= parseAddSub()) ? 1 : 0;
                } else {
                    x = (x > parseAddSub()) ? 1 : 0;
                }
            } else if (ch == '=' && pos + 1 < expression.length() && expression.charAt(pos + 1) == '=') {
                nextChar();
                nextChar();
                x = (x == parseAddSub()) ? 1 : 0;
            } else if (ch == '!' && pos + 1 < expression.length() && expression.charAt(pos + 1) == '=') {
                nextChar();
                nextChar();
                x = (x != parseAddSub()) ? 1 : 0;
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
        if (eat('+')) return +parseFactor(); // unary plus
        if (eat('-')) return -parseFactor(); // unary minus

        long x;
        int startPos = this.pos;
        if (eat('(')) {
            x = parseComparison();
            eat(')');
        } else if ((ch >= '0' && ch <= '9')) {
            while ((ch >= '0' && ch <= '9')) nextChar();
            x = Long.parseLong(expression.substring(startPos, this.pos));
        } else {
            throw new IllegalArgumentException("Unexpected character in factor: " + (char) ch);
        }

        return x;
    }
}
