import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;



public class ShoptCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        PrintStream error = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        error.println("omsh: shopt: optional shell settings are not implemented yet");

        return 2;

    }



    @Override

    public String getName() {

        return "shopt";

    }



    @Override

    public String getDescription() {

        return "Optional shell settings (not implemented yet)";

    }



    @Override

    public String getUsage() {

        return "shopt [options]";

    }

}
