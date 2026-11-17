import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.List;



public class CatCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            try {

                if (stdin != null) {

                    stdin.transferTo(stdout);

                    stdout.flush();

                }

            } catch (Exception e) {

                err.println("omsh: cat: " + e.getMessage());

                return 1;

            }

            return 0;

        }



        int status = 0;

        for (String fileName : args) {

            Path file = shell.resolvePath(fileName);

            if (!Files.exists(file)) {

                err.println("omsh: cat: " + fileName + ": No such file or directory");

                status = 1;

                continue;

            }

            if (Files.isDirectory(file)) {

                err.println("omsh: cat: " + fileName + ": Is a directory");

                status = 1;

                continue;

            }



            try (InputStream in = Files.newInputStream(file)) {

                in.transferTo(stdout);

                stdout.flush();

            } catch (Exception e) {

                err.println("omsh: cat: " + fileName + ": " + e.getMessage());

                status = 1;

            }

        }



        return status;

    }



    @Override

    public String getName() {

        return "cat";

    }



    @Override

    public String getDescription() {

        return "Concatenate and display file contents";

    }



    @Override

    public String getUsage() {

        return "cat [file ...]";

    }

}
