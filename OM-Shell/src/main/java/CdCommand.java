import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.List;



public class CdCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);



        Path targetDir;

        if (args.isEmpty() || args.get(0).equals("~")) {

            targetDir = Path.of(System.getProperty("user.home"));

        } else if (args.get(0).equals("-")) {

            String oldPwd = shell.getVariable("OLDPWD");

            if (oldPwd == null || oldPwd.isEmpty()) {

                err.println("omsh: cd: OLDPWD not set");

                return 1;

            }

            targetDir = Path.of(oldPwd);

            out.println(targetDir);

        } else {

            targetDir = shell.resolvePath(args.get(0));

        }



        if (!Files.exists(targetDir)) {

            err.println("omsh: cd: " + args.get(0) + ": No such file or directory");

            return 1;

        }



        if (!Files.isDirectory(targetDir)) {

            err.println("omsh: cd: " + args.get(0) + ": Not a directory");

            return 1;

        }



        shell.setVariable("OLDPWD", shell.getWorkingDirectory().toAbsolutePath().toString(), true);

        shell.setWorkingDirectory(targetDir.toAbsolutePath().normalize());

        shell.setVariable("PWD", targetDir.toAbsolutePath().normalize().toString(), true);

        return 0;

    }



    @Override

    public String getName() {

        return "cd";

    }



    @Override

    public String getDescription() {

        return "Change the shell working directory";

    }



    @Override

    public String getUsage() {

        return "cd [dir]";

    }

}
