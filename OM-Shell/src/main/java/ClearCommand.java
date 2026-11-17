import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;



public class ClearCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        out.print("\033[H\033[2J");

        out.flush();

        return 0;

    }



    @Override

    public String getName() {

        return "clear";

    }



    @Override

    public String getDescription() {

        return "Clear the terminal screen";

    }



    @Override

    public String getUsage() {

        return "clear";

    }

}
