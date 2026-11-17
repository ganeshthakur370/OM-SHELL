import java.io.InputStream;

import java.io.OutputStream;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.ArrayList;

import java.util.List;

import java.util.regex.Pattern;



public class TestCommand implements BuiltinCommand {



    public enum Mode {

        TEST,

        SINGLE_BRACKET,

        DOUBLE_BRACKET

    }



    private final Mode mode;



    public TestCommand(boolean requireClosingBracket) {

        this.mode = requireClosingBracket ? Mode.SINGLE_BRACKET : Mode.TEST;

    }



    public TestCommand(Mode mode) {

        this.mode = mode;

    }



    public TestCommand(String name) {

        if ("[[".equals(name)) {

            this.mode = Mode.DOUBLE_BRACKET;

        } else if ("[".equals(name)) {

            this.mode = Mode.SINGLE_BRACKET;

        } else {

            this.mode = Mode.TEST;

        }

    }



    @Override

    public int execute(List<String> rawArgs, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        List<String> args = new ArrayList<>(rawArgs);



        if (mode == Mode.SINGLE_BRACKET) {

            if (args.isEmpty() || !args.get(args.size() - 1).equals("]")) {

                return 2;

            }

            args.remove(args.size() - 1);

        } else if (mode == Mode.DOUBLE_BRACKET) {

            if (args.isEmpty() || !args.get(args.size() - 1).equals("]]")) {

                return 2;

            }

            args.remove(args.size() - 1);

        }



        if (args.isEmpty()) {

            return 1;

        }



        boolean result = evaluate(args, shell);

        return result ? 0 : 1;

    }



    public boolean evaluate(List<String> args, Shell shell) {

        if (args.isEmpty()) {

            return false;

        }



        for (int i = 0; i < args.size(); i++) {

            String token = args.get(i);

            if (token.equals("||") || (mode != Mode.DOUBLE_BRACKET && token.equals("-o"))) {

                boolean left = evaluate(args.subList(0, i), shell);

                if (left) return true;

                return evaluate(args.subList(i + 1, args.size()), shell);

            }

        }



        for (int i = 0; i < args.size(); i++) {

            String token = args.get(i);

            if (token.equals("&&") || (mode != Mode.DOUBLE_BRACKET && token.equals("-a"))) {

                boolean left = evaluate(args.subList(0, i), shell);

                if (!left) return false;

                return evaluate(args.subList(i + 1, args.size()), shell);

            }

        }



        if (args.get(0).equals("!")) {

            return !evaluate(args.subList(1, args.size()), shell);

        }



        if (args.get(0).equals("(") && args.get(args.size() - 1).equals(")")) {

            return evaluate(args.subList(1, args.size() - 1), shell);

        }



        if (args.size() == 1) {

            return !args.get(0).isEmpty();

        }



        if (args.size() == 2) {

            String op = args.get(0);

            String target = args.get(1);

            return evaluateUnary(op, target, shell);

        }



        if (args.size() == 3) {

            String left = args.get(0);

            String op = args.get(1);

            String right = args.get(2);

            return evaluateBinary(left, op, right);

        }



        return !args.get(0).isEmpty();

    }



    private boolean evaluateUnary(String op, String target, Shell shell) {

        Path path = shell.resolvePath(target);

        return switch (op) {

            case "-d" -> Files.isDirectory(path);

            case "-f" -> Files.isRegularFile(path);

            case "-e", "-a" -> Files.exists(path);

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

        return switch (op) {

            case "=", "==" -> {

                if (mode == Mode.DOUBLE_BRACKET && hasGlobChars(right)) {

                    yield left.matches(globToRegex(right));

                }

                yield left.equals(right);

            }

            case "!=" -> {

                if (mode == Mode.DOUBLE_BRACKET && hasGlobChars(right)) {

                    yield !left.matches(globToRegex(right));

                }

                yield !left.equals(right);

            }

            case "=~" -> {

                try {

                    yield Pattern.compile(right).matcher(left).find();

                } catch (Exception e) {

                    yield false;

                }

            }

            case "<" -> left.compareTo(right) < 0;

            case ">" -> left.compareTo(right) > 0;

            case "-eq" -> parseLong(left) == parseLong(right);

            case "-ne" -> parseLong(left) != parseLong(right);

            case "-lt" -> parseLong(left) < parseLong(right);

            case "-le" -> parseLong(left) <= parseLong(right);

            case "-gt" -> parseLong(left) > parseLong(right);

            case "-ge" -> parseLong(left) >= parseLong(right);

            default -> false;

        };

    }



    private static boolean hasGlobChars(String s) {

        return s.contains("*") || s.contains("?") || s.contains("[");

    }



    public static String globToRegex(String glob) {

        StringBuilder regex = new StringBuilder("^");

        for (int i = 0; i < glob.length(); i++) {

            char c = glob.charAt(i);

            switch (c) {

                case '*' -> regex.append(".*");

                case '?' -> regex.append(".");

                case '.' -> regex.append("\\.");

                case '\\' -> {

                    if (i + 1 < glob.length()) {

                        regex.append("\\").append(glob.charAt(++i));

                    } else {

                        regex.append("\\\\");

                    }

                }

                case '(', ')', '{', '}', '+', '^', '$', '|' -> regex.append("\\").append(c);

                default -> regex.append(c);

            }

        }

        regex.append("$");

        return regex.toString();

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

        return switch (mode) {

            case DOUBLE_BRACKET -> "[[";

            case SINGLE_BRACKET -> "[";

            case TEST -> "test";

        };

    }



    @Override

    public String getDescription() {

        return "Evaluate condition expressions (supports test, [, and [[ with regex/glob)";

    }



    @Override

    public String getUsage() {

        return switch (mode) {

            case DOUBLE_BRACKET -> "[[ expr ]]";

            case SINGLE_BRACKET -> "[ expr ]";

            case TEST -> "test expr";

        };

    }

}
