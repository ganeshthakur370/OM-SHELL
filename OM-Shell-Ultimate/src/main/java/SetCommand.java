
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Handles shell option requests that are not implemented yet. */
public class SetCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout,
                       OutputStream stderr, Shell shell) {
        PrintStream error = new PrintStream(stderr, true, StandardCharsets.UTF_8);
        error.println("omsh: set: shell option handling is not implemented yet");
        return 2;
    }

    @Override
    public String getName() {
        return "set";
    }

    @Override
    public String getDescription() {
        return "Shell options (limited; not implemented yet)";
    }

    @Override
    public String getUsage() {
        return "set [options]";
    }
}
