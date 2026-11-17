
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Displays or manages shell command history.
 */
public class HistoryCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        if (!args.isEmpty() && args.get(0).equals("-c")) {
            shell.clearHistory();
            return 0;
        }

        List<String> history = shell.getHistory();
        for (int i = 0; i < history.size(); i++) {
            out.printf("%5d  %s%n", i + 1, history.get(i));
        }

        return 0;
    }

    @Override
    public String getName() {
        return "history";
    }

    @Override
    public String getDescription() {
        return "Display or manipulate the history list";
    }

    @Override
    public String getUsage() {
        return "history [-c]";
    }
}
