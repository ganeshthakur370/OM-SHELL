
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates conditional expressions (supports both 'test' and '[' builtins).
 */
public class TestCommand implements BuiltinCommand {

    private final boolean requireClosingBracket;

    public TestCommand(boolean requireClosingBracket) {
        this.requireClosingBracket = requireClosingBracket;
    }

    @Override
    public int execute(List<String> rawArgs, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        List<String> args = new ArrayList<>(rawArgs);

        if (requireClosingBracket) {
            if (args.isEmpty() || !args.get(args.size() - 1).equals("]")) {
                return 2; // Missing ']'
            }
            args.remove(args.size() - 1);
        }

        if (args.isEmpty()) {
            return 1;
        }

        boolean result = evaluate(args, shell);
        return result ? 0 : 1;
    }

    private boolean evaluate(List<String> args, Shell shell) {
        if (args.isEmpty()) {
            return false;
        }

        // Handle negation prefix '!'
        if (args.get(0).equals("!")) {
            return !evaluate(args.subList(1, args.size()), shell);
        }

        // Single argument test: [ string ] -> true if non-empty
        if (args.size() == 1) {
            return !args.get(0).isEmpty();
        }

        // Two arguments: unary operator
        if (args.size() == 2) {
            String op = args.get(0);
            String target = args.get(1);
            return evaluateUnary(op, target, shell);
        }

        // Three arguments: binary operator
        if (args.size() == 3) {
            String left = args.get(0);
            String op = args.get(1);
            String right = args.get(2);
            return evaluateBinary(left, op, right);
        }

        // Fallback for complex combinations: evaluate left side
        if (args.size() >= 4 && (args.get(3).equals("-a") || args.get(3).equals("&&"))) {
            boolean left = evaluate(args.subList(0, 3), shell);
            return left && evaluate(args.subList(4, args.size()), shell);
        }
        if (args.size() >= 4 && (args.get(3).equals("-o") || args.get(3).equals("||"))) {
            boolean left = evaluate(args.subList(0, 3), shell);
            return left || evaluate(args.subList(4, args.size()), shell);
        }

        return !args.get(0).isEmpty();
    }

    private boolean evaluateUnary(String op, String target, Shell shell) {
        Path path = shell.resolvePath(target);
        return switch (op) {
            case "-d" -> Files.isDirectory(path);
            case "-f" -> Files.isRegularFile(path);
            case "-e" -> Files.exists(path);
            case "-s" -> {
                try {
                    yield Files.exists(path) && Files.size(path) > 0;
                } catch (Exception e) {
                    yield false;
                }
            }
            case "-r" -> Files.isReadable(path);
            case "-w" -> Files.isWritable(path);
            case "-x" -> Files.isExecutable(path);
            case "-z" -> target.isEmpty();
            case "-n" -> !target.isEmpty();
            default -> false;
        };
    }

    private boolean evaluateBinary(String left, String op, String right) {
        switch (op) {
            case "=", "==" -> {
                return left.equals(right);
            }
            case "!=" -> {
                return !left.equals(right);
            }
            case "-eq" -> {
                return parseLong(left) == parseLong(right);
            }
            case "-ne" -> {
                return parseLong(left) != parseLong(right);
            }
            case "-lt" -> {
                return parseLong(left) < parseLong(right);
            }
            case "-le" -> {
                return parseLong(left) <= parseLong(right);
            }
            case "-gt" -> {
                return parseLong(left) > parseLong(right);
            }
            case "-ge" -> {
                return parseLong(left) >= parseLong(right);
            }
            default -> {
                return false;
            }
        }
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public String getName() {
        return requireClosingBracket ? "[" : "test";
    }

    @Override
    public String getDescription() {
        return "Evaluate condition expression";
    }

    @Override
    public String getUsage() {
        return requireClosingBracket ? "[ expr ]" : "test expr";
    }
}
