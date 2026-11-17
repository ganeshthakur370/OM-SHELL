import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.List;



public class EchoCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);



        boolean newline = true;

        boolean interpretEscapes = false;

        int argIndex = 0;



        while (argIndex < args.size()) {

            String arg = args.get(argIndex);

            if (arg.equals("-n")) {

                newline = false;

                argIndex++;

            } else if (arg.equals("-e")) {

                interpretEscapes = true;

                argIndex++;

            } else if (arg.equals("-ne") || arg.equals("-en")) {

                newline = false;

                interpretEscapes = true;

                argIndex++;

            } else {

                break;

            }

        }



        List<String> outputArgs = new ArrayList<>();

        for (int i = argIndex; i < args.size(); i++) {

            String text = args.get(i);

            if (interpretEscapes) {

                text = decodeEscapes(text);

            }

            outputArgs.add(text);

        }



        String message = String.join(" ", outputArgs);

        if (newline) {

            out.println(message);

        } else {

            out.print(message);

            out.flush();

        }



        return 0;

    }



    private String decodeEscapes(String s) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {

            char c = s.charAt(i);

            if (c == '\\' && i + 1 < s.length()) {

                char next = s.charAt(i + 1);

                switch (next) {

                    case 'n' -> sb.append('\n');

                    case 't' -> sb.append('\t');

                    case 'r' -> sb.append('\r');

                    case '\\' -> sb.append('\\');

                    case '0' -> sb.append('\0');

                    default -> {

                        sb.append(c);

                        sb.append(next);

                    }

                }

                i++;

            } else {

                sb.append(c);

            }

        }

        return sb.toString();

    }



    @Override

    public String getName() {

        return "echo";

    }



    @Override

    public String getDescription() {

        return "Write arguments to standard output";

    }



    @Override

    public String getUsage() {

        return "echo [-n] [-e] [string ...]";

    }

}
