import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.attribute.FileTime;

import java.util.List;



public class TouchCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            err.println("omsh: touch: missing file operand");

            return 1;

        }



        int status = 0;

        for (String fileName : args) {

            Path file = shell.resolvePath(fileName);

            try {

                if (Files.exists(file)) {

                    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));

                } else {

                    if (file.getParent() != null) {

                        Files.createDirectories(file.getParent());

                    }

                    Files.createFile(file);

                }

            } catch (Exception e) {

                err.println("omsh: touch: cannot touch '" + fileName + "': " + e.getMessage());

                status = 1;

            }

        }



        return status;

    }



    @Override

    public String getName() {

        return "touch";

    }



    @Override

    public String getDescription() {

        return "Change file timestamps or create empty files";

    }



    @Override

    public String getUsage() {

        return "touch file ...";

    }

}
