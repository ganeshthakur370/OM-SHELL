
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Interface implemented by all in-process shell built-in commands.
 */
public interface BuiltinCommand {

    /**
     * Executes the built-in command with the given arguments and streams.
     *
     * @param args   Command arguments (excluding command name itself)
     * @param stdin  Input stream for standard input
     * @param stdout Output stream for standard output
     * @param stderr Output stream for standard error
     * @param shell  The active shell session instance
     * @return Exit status (0 for success, non-zero for error)
     * @throws Exception If an unhandled execution error occurs
     */
    int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) throws Exception;

    /**
     * The primary command name (e.g. "cd", "echo").
     */
    String getName();

    /**
     * A brief description of the command.
     */
    String getDescription();

    /**
     * Syntax usage string.
     */
    String getUsage();
}
