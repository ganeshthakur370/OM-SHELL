
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats and writes text to standard output according to a format string.
 */
public class PrintfCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        if (args.isEmpty()) {
            return 0;
        }

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        String format = decodeEscapes(args.get(0));
        List<String> formatArgs = args.subList(1, args.size());

        if (formatArgs.isEmpty()) {
            out.print(format);
            out.flush();
            return 0;
        }

        // Map POSIX format specifiers to Java format specifiers
        try {
            List<Object> typedArgs = new ArrayList<>();
            for (String arg : formatArgs) {
                try {
                    typedArgs.add(Long.parseLong(arg));
                } catch (NumberFormatException e) {
                    try {
                        typedArgs.add(Double.parseDouble(arg));
                    } catch (NumberFormatException ignored) {
                        typedArgs.add(arg);
                    }
                }
            }
            out.print(String.format(format, typedArgs.toArray()));
            out.flush();
        } catch (Exception e) {
            // Fallback: simple string replacement or direct print
            out.print(format);
            out.flush();
        }

        return 0;
    }

    private String decodeEscapes(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    default -> {
                        sb.append(c);
                        sb.append(next);
                    }
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return "printf";
    }

    @Override
    public String getDescription() {
        return "Format and print data";
    }

    @Override
    public String getUsage() {
        return "printf format [arguments ...]";
    }
}
