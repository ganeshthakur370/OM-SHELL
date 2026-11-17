import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;



public class PwdCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        out.println(shell.getWorkingDirectory().toAbsolutePath().normalize());

        return 0;

    }



    @Override

    public String getName() {

        return "pwd";

    }



    @Override

    public String getDescription() {

        return "Print the current working directory";

    }



    @Override

    public String getUsage() {

        return "pwd";

    }

}
