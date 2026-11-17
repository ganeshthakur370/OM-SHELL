import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;

import java.util.Map;



public class EnvCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        Map<String, String> vars = shell.getVariables();



        for (Map.Entry<String, String> entry : vars.entrySet()) {

            out.println(entry.getKey() + "=" + entry.getValue());

        }



        return 0;

    }



    @Override

    public String getName() {

        return "env";

    }



    @Override

    public String getDescription() {

        return "Display the environment variables";

    }



    @Override

    public String getUsage() {

        return "env";

    }

}
