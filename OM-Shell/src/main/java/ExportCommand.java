import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;

import java.util.Map;



public class ExportCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            Map<String, String> vars = shell.getVariables();

            for (Map.Entry<String, String> entry : vars.entrySet()) {

                out.printf("export %s=\"%s\"%n", entry.getKey(), entry.getValue());

            }

            return 0;

        }



        for (String arg : args) {

            int equalsIdx = arg.indexOf('=');

            if (equalsIdx > 0) {

                String key = arg.substring(0, equalsIdx);

                String val = arg.substring(equalsIdx + 1);



                if (val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {

                    val = val.substring(1, val.length() - 1);

                }

                shell.setVariable(key, val, true);

            } else {



                String existing = shell.getVariable(arg);

                if (existing != null) {

                    shell.setVariable(arg, existing, true);

                }

            }

        }



        return 0;

    }



    @Override

    public String getName() {

        return "export";

    }



    @Override

    public String getDescription() {

        return "Set export attribute for shell variables";

    }



    @Override

    public String getUsage() {

        return "export [name[=value] ...]";

    }

}
