
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Registers shell exit and supported operating-system signal handlers. */
public class TrapCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout,
                       OutputStream stderr, Shell shell) {
        PrintStream output = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        if (args.isEmpty()) {
            for (Map.Entry<String, String> entry : shell.getTraps().entrySet()) {
                output.println("trap -- '" + entry.getValue().replace("'", "'\\''") + "' " + entry.getKey());
            }
            return 0;
        }

        if (args.size() == 1 && args.get(0).equals("-l")) {
            output.println("EXIT 0 INT 2 QUIT 3 HUP 1 TERM 15 PIPE 13 ALRM 14 USR1 10 USR2 12");
            return 0;
        }

        if (args.size() < 2) {
            error.println("usage: trap [-l] [command] signal...");
            return 2;
        }

        boolean reset = args.get(0).equals("-");
        String command = reset ? "" : args.get(0);
        int firstSignal = 1;

        for (int index = firstSignal; index < args.size(); index++) {
            String signal = normalizeSignal(args.get(index));
            if (signal == null) {
                error.println("omsh: trap: invalid signal: " + args.get(index));
                return 2;
            }
            if (!signal.equals("EXIT") && !shell.getSignalManager().supports(signal)) {
                error.println("omsh: trap: signal " + signal + " is not supported on this platform");
                return 2;
            }
            if (reset) {
                shell.removeTrap(signal);
            } else {
                shell.registerTrap(signal, command);
            }
        }
        return 0;
    }

    private String normalizeSignal(String value) {
        String signal = value.toUpperCase();
        if (signal.equals("0")) return "EXIT";
        if (signal.startsWith("SIG")) signal = signal.substring(3);
        return switch (signal) {
            case "EXIT", "INT", "TERM", "HUP", "QUIT", "USR1", "USR2", "ALRM", "PIPE" -> signal;
            default -> null;
        };
    }

    @Override public String getName() { return "trap"; }
    @Override public String getDescription() { return "Register shell exit and supported OS signal traps"; }
    @Override public String getUsage() { return "trap [-l] [command] signal..."; }
}
