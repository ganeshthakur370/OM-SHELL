import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;



public class TrCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            err.println("omsh: tr: missing operand");

            return 1;

        }



        boolean delete = false;

        String set1 = null;

        String set2 = null;



        for (String arg : args) {

            if (arg.equals("-d") || arg.equals("--delete")) {

                delete = true;

            } else if (set1 == null) {

                set1 = unescape(arg);

            } else if (set2 == null) {

                set2 = unescape(arg);

            }

        }



        if (set1 == null) {

            err.println("omsh: tr: missing operand");

            return 1;

        }



        if (stdin == null) {

            return 0;

        }



        try {

            int b;

            if (delete) {

                while ((b = stdin.read()) != -1) {

                    char c = (char) b;

                    if (set1.indexOf(c) == -1) {

                        stdout.write(b);

                    }

                }

            } else {

                if (set2 == null) set2 = "";

                while ((b = stdin.read()) != -1) {

                    char c = (char) b;

                    int idx = set1.indexOf(c);

                    if (idx != -1 && idx < set2.length()) {

                        stdout.write(set2.charAt(idx));

                    } else if (idx != -1 && !set2.isEmpty()) {

                        stdout.write(set2.charAt(set2.length() - 1));

                    } else {

                        stdout.write(b);

                    }

                }

            }

            stdout.flush();

            return 0;

        } catch (Exception e) {

            err.println("omsh: tr: " + e.getMessage());

            return 1;

        }

    }



    private String unescape(String s) {

        return s.replace("\\n", "\n")

                .replace("\\t", "\t")

                .replace("\\r", "\r")

                .replace("\\\\", "\\");

    }



    @Override

    public String getName() {

        return "tr";

    }



    @Override

    public String getDescription() {

        return "Translate or delete characters";

    }



    @Override

    public String getUsage() {

        return "tr [-d] SET1 [SET2]";

    }

}
