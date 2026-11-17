import java.io.File;

import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.Comparator;

import java.util.List;



public class RmCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            err.println("omsh: rm: missing operand");

            return 1;

        }



        boolean recursive = false;

        boolean force = false;

        int status = 0;



        for (String arg : args) {

            if (arg.equals("-r") || arg.equals("-R") || arg.equals("--recursive")) {

                recursive = true;

                continue;

            }

            if (arg.equals("-f") || arg.equals("--force")) {

                force = true;

                continue;

            }

            if (arg.equals("-rf") || arg.equals("-fr")) {

                recursive = true;

                force = true;

                continue;

            }

            if (arg.startsWith("-")) {

                continue;

            }



            Path path = shell.resolvePath(arg);

            if (!Files.exists(path)) {

                if (!force) {

                    err.println("omsh: rm: cannot remove '" + arg + "': No such file or directory");

                    status = 1;

                }

                continue;

            }



            try {

                if (Files.isDirectory(path)) {

                    if (!recursive) {

                        err.println("omsh: rm: cannot remove '" + arg + "': Is a directory");

                        status = 1;

                        continue;

                    }



                    try (var stream = Files.walk(path)) {

                        stream.sorted(Comparator.reverseOrder())

                              .map(Path::toFile)

                              .forEach(File::delete);

                    }

                } else {

                    Files.deleteIfExists(path);

                }

            } catch (Exception e) {

                if (!force) {

                    err.println("omsh: rm: cannot remove '" + arg + "': " + e.getMessage());

                    status = 1;

                }

            }

        }



        return status;

    }



    @Override

    public String getName() {

        return "rm";

    }



    @Override

    public String getDescription() {

        return "Remove files or directories";

    }



    @Override

    public String getUsage() {

        return "rm [-r] [-f] file ...";

    }

}
