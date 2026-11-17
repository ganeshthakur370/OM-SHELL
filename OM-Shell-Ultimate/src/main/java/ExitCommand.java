
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Causes the shell to exit with the specified status.
 */
public class ExitCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        int status = shell.getLastExitCode();

        if (!args.isEmpty()) {
            try {
                status = Integer.parseInt(args.get(0));
            } catch (NumberFormatException e) {
                PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);
                err.println("omsh: exit: " + args.get(0) + ": numeric argument required");
                status = 255;
            }
        }

        shell.setRunning(false);
        shell.setLastExitCode(status);
        return status;
    }

    @Override
    public String getName() {
        return "exit";
    }

    @Override
    public String getDescription() {
        return "Exit the shell session";
    }

    @Override
    public String getUsage() {
        return "exit [status]";
    }
}
