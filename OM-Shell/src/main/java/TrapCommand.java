import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;

import java.util.Map;



public class TrapCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        PrintStream output = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        PrintStream error = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            for (Map.Entry<String, String> entry : shell.getAllTraps().entrySet()) {

                output.println("trap -- '" + entry.getValue().replace("'", "'\\''") + "' " + entry.getKey());

            }

            return 0;

        }



        if (args.get(0).equals("-l")) {

            StringBuilder sb = new StringBuilder();

            sb.append("0) EXIT\n");

            for (Map.Entry<String, Integer> e : SignalManager.SIGNAL_NUMBERS.entrySet()) {

                sb.append(e.getValue()).append(") SIG").append(e.getKey()).append(" ");

                if (e.getValue() % 4 == 0) sb.append("\n");

            }

            output.println(sb.toString().trim());

            return 0;

        }



        if (args.get(0).equals("-p")) {

            if (args.size() == 1) {

                for (Map.Entry<String, String> entry : shell.getAllTraps().entrySet()) {

                    output.println("trap -- '" + entry.getValue().replace("'", "'\\''") + "' " + entry.getKey());

                }

            } else {

                for (int i = 1; i < args.size(); i++) {

                    String sig = SignalManager.normalizeSignalName(args.get(i));

                    String cmd = shell.getTrap(sig);

                    if (cmd != null) {

                        output.println("trap -- '" + cmd.replace("'", "'\\''") + "' " + sig);

                    }

                }

            }

            return 0;

        }



        if (args.size() < 2) {

            error.println("usage: trap [-lp] [[arg] sigspec ...]");

            return 2;

        }



        boolean reset = args.get(0).equals("-") || args.get(0).equals("");

        String command = reset ? null : args.get(0);

        int firstSignal = 1;



        for (int index = firstSignal; index < args.size(); index++) {

            String rawSig = args.get(index);

            String signal = SignalManager.normalizeSignalName(rawSig);

            if (signal == null || signal.isEmpty()) {

                error.println("omsh: trap: invalid signal: " + rawSig);

                return 2;

            }



            if (reset) {

                shell.registerTrap(signal, null);

            } else {

                shell.registerTrap(signal, command);

            }

        }

        return 0;

    }



    @Override public String getName() { return "trap"; }

    @Override public String getDescription() { return "Register shell exit, pseudo-event, and OS signal traps"; }

    @Override public String getUsage() { return "trap [-lp] [[command] signal...]"; }

}
