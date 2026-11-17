import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;



public class ShiftCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        int count = 1;

        if (!args.isEmpty()) {

            try {

                count = Integer.parseInt(args.get(0).trim());

            } catch (NumberFormatException e) {

                new PrintStream(stderr, true, StandardCharsets.UTF_8).println("omsh: shift: " + args.get(0) + ": numeric argument required");

                return 1;

            }

        }



        if (count < 0) {

            new PrintStream(stderr, true, StandardCharsets.UTF_8).println("omsh: shift: " + count + ": shift count out of range");

            return 1;

        }



        int currentCount = shell.getPositionalParameters().size();

        if (count > currentCount) {

            new PrintStream(stderr, true, StandardCharsets.UTF_8).println("omsh: shift: " + count + ": shift count out of range");

            return 1;

        }



        shell.shift(count);

        return 0;

    }



    @Override

    public String getName() {

        return "shift";

    }



    @Override

    public String getDescription() {

        return "Shift positional parameters";

    }



    @Override

    public String getUsage() {

        return "shift [n]";

    }

}
