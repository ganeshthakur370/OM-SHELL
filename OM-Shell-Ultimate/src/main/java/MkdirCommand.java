
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Creates directories.
 */
public class MkdirCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        if (args.isEmpty()) {
            err.println("omsh: mkdir: missing operand");
            return 1;
        }

        boolean parents = false;
        int status = 0;

        for (String arg : args) {
            if (arg.equals("-p") || arg.equals("--parents")) {
                parents = true;
                continue;
            }

            Path dir = shell.resolvePath(arg);
            try {
                if (parents) {
                    Files.createDirectories(dir);
                } else {
                    Files.createDirectory(dir);
                }
            } catch (FileAlreadyExistsException e) {
                if (!parents) {
                    err.println("omsh: mkdir: cannot create directory '" + arg + "': File exists");
                    status = 1;
                }
            } catch (Exception e) {
                err.println("omsh: mkdir: cannot create directory '" + arg + "': " + e.getMessage());
                status = 1;
            }
        }

        return status;
    }

    @Override
    public String getName() {
        return "mkdir";
    }

    @Override
    public String getDescription() {
        return "Create directories";
    }

    @Override
    public String getUsage() {
        return "mkdir [-p] directory ...";
    }
}
