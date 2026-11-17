
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Main entry point for OM Shell Ultimate (Pure Java Edition).
 *
 * Provides a fully Java-implemented POSIX-compatible shell environment
 * supporting interactive REPL, script execution, pipelines, redirections,
 * variable handling, built-in commands, and background job management.
 */
public class Main {

    public static final String VERSION = "2.1.1 (Java 25)";

    public static void main(String[] args) {
        Shell shell = new Shell();
        int exitCode = 0;

        try {
            if (args.length == 0) {
                // Interactive mode
                shell.startInteractive();
                exitCode = shell.getLastExitCode();
            } else if (args[0].equals("-c")) {
                // Single command execution: -c "command string"
                if (args.length < 2) {
                    System.err.println("omsh: -c: option requires an argument");
                    exitCode = 2;
                } else {
                    exitCode = shell.executeLine(args[1]);
                }
            } else if (args[0].equals("-v") || args[0].equals("--version")) {
                System.out.println("OM Shell Ultimate version " + VERSION);
                exitCode = 0;
            } else if (args[0].equals("-h") || args[0].equals("--help")) {
                printUsage();
                exitCode = 0;
            } else {
                // Script execution mode: script_path [args...]
                Path scriptPath = Path.of(args[0]);
                String[] scriptArgs = Arrays.copyOfRange(args, 1, args.length);
                exitCode = shell.executeScript(scriptPath, scriptArgs);
            }
        } catch (Exception e) {
            System.err.println("omsh: unexpected error: " + e.getMessage());
            exitCode = 1;
        }

        System.exit(exitCode);
    }

    private static void printUsage() {
        System.out.println("Usage: omsh [OPTION] [SCRIPT [ARGUMENTS...]]");
        System.out.println("OM Shell Ultimate - A pure Java POSIX shell implementation.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -c COMMAND       Execute COMMAND string and exit");
        System.out.println("  -h, --help       Display this help message and exit");
        System.out.println("  -v, --version    Output version information and exit");
        System.out.println();
        System.out.println("If no script or option is specified, omsh starts an interactive session.");
    }
}
