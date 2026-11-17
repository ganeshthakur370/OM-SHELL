
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches for patterns in files or standard input.
 */
public class GrepCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        if (args.isEmpty()) {
            err.println("omsh: grep: missing pattern");
            return 2;
        }

        boolean ignoreCase = false;
        boolean invertMatch = false;
        String patternStr = null;
        List<String> files = new ArrayList<>();

        for (String arg : args) {
            if (arg.equals("-i")) {
                ignoreCase = true;
            } else if (arg.equals("-v")) {
                invertMatch = true;
            } else if (patternStr == null) {
                patternStr = arg;
            } else {
                files.add(arg);
            }
        }

        if (patternStr == null) {
            err.println("omsh: grep: missing pattern");
            return 2;
        }

        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr, flags);
        } catch (Exception e) {
            pattern = Pattern.compile(Pattern.quote(patternStr), flags);
        }

        boolean matchedAny = false;

        if (files.isEmpty() || (files.size() == 1 && files.get(0).equals("-"))) {
            if (stdin != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        Matcher m = pattern.matcher(line);
                        boolean match = m.find();
                        if (match ^ invertMatch) {
                            out.println(line);
                            matchedAny = true;
                        }
                    }
                } catch (Exception e) {
                    err.println("omsh: grep: " + e.getMessage());
                }
            }
        } else {
            for (String fileStr : files) {
                Path path = shell.resolvePath(fileStr);
                if (!Files.exists(path)) {
                    err.println("omsh: grep: " + fileStr + ": No such file or directory");
                    continue;
                }
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher m = pattern.matcher(line);
                        boolean match = m.find();
                        if (match ^ invertMatch) {
                            if (files.size() > 1) {
                                out.println(fileStr + ":" + line);
                            } else {
                                out.println(line);
                            }
                            matchedAny = true;
                        }
                    }
                } catch (Exception e) {
                    err.println("omsh: grep: " + fileStr + ": " + e.getMessage());
                }
            }
        }

        return matchedAny ? 0 : 1;
    }

    @Override
    public String getName() {
        return "grep";
    }

    @Override
    public String getDescription() {
        return "Print lines that match patterns";
    }

    @Override
    public String getUsage() {
        return "grep [-i] [-v] PATTERN [FILE...]";
    }
}
