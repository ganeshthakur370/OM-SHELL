import java.io.BufferedReader;

import java.io.InputStream;

import java.io.InputStreamReader;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.List;



public class ReadCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        boolean raw = false;

        String prompt = null;

        String arrayName = null;

        List<String> varNames = new ArrayList<>();



        int i = 0;

        while (i < args.size()) {

            String arg = args.get(i);

            if (arg.equals("-r")) {

                raw = true;

                i++;

            } else if (arg.equals("-p") && i + 1 < args.size()) {

                prompt = args.get(++i);

                i++;

            } else if (arg.equals("-a") && i + 1 < args.size()) {

                arrayName = args.get(++i);

                i++;

            } else if (arg.equals("--")) {

                i++;

                while (i < args.size()) {

                    varNames.add(args.get(i++));

                }

                break;

            } else if (arg.startsWith("-")) {



                i++;

            } else {

                varNames.add(arg);

                i++;

            }

        }



        if (prompt != null && !prompt.isEmpty()) {

            PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

            err.print(prompt);

            err.flush();

        }



        try {

            BufferedReader reader = new BufferedReader(new InputStreamReader(stdin, StandardCharsets.UTF_8));

            String line = reader.readLine();

            if (line == null) {

                return 1;

            }



            if (!raw) {



                if (line.endsWith("\\")) {

                    StringBuilder full = new StringBuilder(line.substring(0, line.length() - 1));

                    String next;

                    while ((next = reader.readLine()) != null) {

                        if (next.endsWith("\\")) {

                            full.append(next.substring(0, next.length() - 1));

                        } else {

                            full.append(next);

                            break;

                        }

                    }

                    line = full.toString();

                }

            }



            String[] tokens = line.trim().isEmpty() ? new String[0] : line.trim().split("\\s+");



            if (arrayName != null) {

                shell.getIndexedArray(arrayName);

                for (int idx = 0; idx < tokens.length; idx++) {

                    shell.setIndexedArrayValue(arrayName, idx, tokens[idx]);

                }

                return 0;

            }



            if (varNames.isEmpty()) {

                shell.setVariable("REPLY", line, false);

                return 0;

            }



            if (varNames.size() == 1) {

                shell.setVariable(varNames.get(0), line.trim(), false);

                return 0;

            }



            for (int v = 0; v < varNames.size(); v++) {

                String var = varNames.get(v);

                if (v == varNames.size() - 1) {



                    if (v < tokens.length) {

                        StringBuilder remainder = new StringBuilder();

                        for (int rem = v; rem < tokens.length; rem++) {

                            if (remainder.length() > 0) remainder.append(" ");

                            remainder.append(tokens[rem]);

                        }

                        shell.setVariable(var, remainder.toString(), false);

                    } else {

                        shell.setVariable(var, "", false);

                    }

                } else {

                    shell.setVariable(var, v < tokens.length ? tokens[v] : "", false);

                }

            }

            return 0;



        } catch (Exception e) {

            return 1;

        }

    }



    @Override public String getName() { return "read"; }

    @Override public String getDescription() { return "Read a line from standard input and assign fields to variables"; }

    @Override public String getUsage() { return "read [-r] [-p prompt] [-a array] [name ...]"; }

}
