import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Path;

import java.util.List;



public class TypeCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            return 0;

        }



        int status = 0;

        for (String name : args) {

            String alias = shell.getAlias(name);

            if (alias != null) {

                out.printf("%s is aliased to `%s'%n", name, alias);

                continue;

            }



            if (shell.hasFunction(name)) {

                out.printf("%s is a shell function%n", name);

                continue;

            }



            if (shell.getBuiltinRegistry().isBuiltin(name)) {

                out.printf("%s is a shell builtin%n", name);

                continue;

            }



            Path executable = shell.findExecutable(name);

            if (executable != null) {

                out.printf("%s is %s%n", name, executable);

                continue;

            }



            err.printf("omsh: type: %s: not found%n", name);

            status = 1;

        }



        return status;

    }



    @Override

    public String getName() {

        return "type";

    }



    @Override

    public String getDescription() {

        return "Display information about command type";

    }



    @Override

    public String getUsage() {

        return "type name ...";

    }

}
