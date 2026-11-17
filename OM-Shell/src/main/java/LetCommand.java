import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;



public class LetCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        if (args.isEmpty()) {

            new PrintStream(stderr, true, StandardCharsets.UTF_8).println("omsh: let: expression expected");

            return 1;

        }



        long lastResult = 0;

        for (String arg : args) {

            try {

                lastResult = ArithmeticEvaluator.evaluate(arg, shell.getMutableVariables());

            } catch (Exception e) {

                new PrintStream(stderr, true, StandardCharsets.UTF_8).println("omsh: let: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));

                return 1;

            }

        }



        return (lastResult != 0) ? 0 : 1;

    }



    @Override

    public String getName() {

        return "let";

    }



    @Override

    public String getDescription() {

        return "Evaluate arithmetic expressions";

    }



    @Override

    public String getUsage() {

        return "let arg [arg ...]";

    }

}
