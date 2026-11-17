
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Suspends execution for the specified number of seconds.
 */
public class SleepCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        if (args.isEmpty()) {
            PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);
            err.println("omsh: sleep: missing operand");
            return 1;
        }

        try {
            double seconds = Double.parseDouble(args.get(0));
            long millis = (long) (seconds * 1000);
            if (millis > 0) {
                Thread.sleep(millis);
            }
            return 0;
        } catch (NumberFormatException e) {
            PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);
            err.println("omsh: sleep: invalid time interval '" + args.get(0) + "'");
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        }
    }

    @Override
    public String getName() {
        return "sleep";
    }

    @Override
    public String getDescription() {
        return "Delay for a specified amount of time";
    }

    @Override
    public String getUsage() {
        return "sleep NUMBER[s]";
    }
}
