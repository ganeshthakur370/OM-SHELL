import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.List;



public class SourceCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            err.println("omsh: source: filename argument required");

            return 2;

        }



        String scriptName = args.get(0);

        Path scriptPath = shell.resolvePath(scriptName);



        if (!Files.exists(scriptPath)) {

            err.println("omsh: source: " + scriptName + ": file not found");

            return 1;

        }



        String[] scriptArgs = args.subList(1, args.size()).toArray(String[]::new);

        try {

            return shell.executeScript(scriptPath, scriptArgs);

        } catch (Exception e) {

            err.println("omsh: source: " + e.getMessage());

            return 1;

        }

    }



    @Override

    public String getName() {

        return "source";

    }



    @Override

    public String getDescription() {

        return "Execute commands from a file in the current shell";

    }



    @Override

    public String getUsage() {

        return "source filename [arguments]";

    }

}
