import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Path;

import java.util.List;



public class WhichCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            return 0;

        }



        int status = 0;

        for (String name : args) {

            if (shell.getBuiltinRegistry().isBuiltin(name)) {

                out.println(name + ": shell built-in command");

                continue;

            }



            Path executable = shell.findExecutable(name);

            if (executable != null) {

                out.println(executable);

            } else {

                err.println("omsh: which: no " + name + " in (" + shell.getVariable("PATH") + ")");

                status = 1;

            }

        }



        return status;

    }



    @Override

    public String getName() {

        return "which";

    }



    @Override

    public String getDescription() {

        return "Locate a command";

    }



    @Override

    public String getUsage() {

        return "which name ...";

    }

}
