import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.List;

import java.util.Map;



public class SetCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        PrintStream output = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        PrintStream error = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            for (Map.Entry<String, Boolean> entry : shell.getOptions().entrySet()) {

                output.printf("%-15s %s%n", entry.getKey(), entry.getValue() ? "on" : "off");

            }

            return 0;

        }



        int i = 0;

        while (i < args.size()) {

            String arg = args.get(i);



            if (arg.equals("--")) {

                i++;

                List<String> positionals = new ArrayList<>();

                while (i < args.size()) {

                    positionals.add(args.get(i++));

                }

                shell.setPositionalParameters(positionals);

                return 0;

            }



            if (arg.equals("-o")) {

                if (i + 1 < args.size()) {

                    shell.setOption(args.get(++i), true);

                } else {

                    for (Map.Entry<String, Boolean> entry : shell.getOptions().entrySet()) {

                        output.printf("set %so %s%n", entry.getValue() ? "-" : "+", entry.getKey());

                    }

                }

                i++;

                continue;

            }



            if (arg.equals("+o")) {

                if (i + 1 < args.size()) {

                    shell.setOption(args.get(++i), false);

                } else {

                    for (Map.Entry<String, Boolean> entry : shell.getOptions().entrySet()) {

                        output.printf("set %so %s%n", entry.getValue() ? "-" : "+", entry.getKey());

                    }

                }

                i++;

                continue;

            }



            if (arg.startsWith("-") && arg.length() > 1) {

                for (int j = 1; j < arg.length(); j++) {

                    char c = arg.charAt(j);

                    switch (c) {

                        case 'e' -> shell.setOption("errexit", true);

                        case 'u' -> shell.setOption("nounset", true);

                        case 'x' -> shell.setOption("xtrace", true);

                        default -> {

                            error.println("omsh: set: -" + c + ": invalid option");

                            return 2;

                        }

                    }

                }

                i++;

                continue;

            }



            if (arg.startsWith("+") && arg.length() > 1) {

                for (int j = 1; j < arg.length(); j++) {

                    char c = arg.charAt(j);

                    switch (c) {

                        case 'e' -> shell.setOption("errexit", false);

                        case 'u' -> shell.setOption("nounset", false);

                        case 'x' -> shell.setOption("xtrace", false);

                        default -> {

                            error.println("omsh: set: +" + c + ": invalid option");

                            return 2;

                        }

                    }

                }

                i++;

                continue;

            }



            List<String> positionals = new ArrayList<>();

            while (i < args.size()) {

                positionals.add(args.get(i++));

            }

            shell.setPositionalParameters(positionals);

            break;

        }



        return 0;

    }



    @Override

    public String getName() {

        return "set";

    }



    @Override

    public String getDescription() {

        return "Set or unset shell options and positional parameters";

    }



    @Override

    public String getUsage() {

        return "set [-e|+e] [-u|+u] [-x|+x] [-o option|+o option] [-- [args...]]";

    }

}
