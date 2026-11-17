import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.List;



public class MktempCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        boolean directory = false;

        String prefix = "omsh_tmp_";



        for (String arg : args) {

            if (arg.equals("-d") || arg.equals("--directory")) {

                directory = true;

            } else if (!arg.startsWith("-")) {

                prefix = arg.replace("X", "");

                if (prefix.isEmpty()) prefix = "omsh_";

            }

        }



        try {

            if (directory) {

                Path tempDir = Files.createTempDirectory(prefix);

                out.println(tempDir.toAbsolutePath().normalize());

            } else {

                Path tempFile = Files.createTempFile(prefix, ".tmp");

                out.println(tempFile.toAbsolutePath().normalize());

            }

            return 0;

        } catch (Exception e) {

            err.println("omsh: mktemp: " + e.getMessage());

            return 1;

        }

    }



    @Override

    public String getName() {

        return "mktemp";

    }



    @Override

    public String getDescription() {

        return "Create temporary files or directories";

    }



    @Override

    public String getUsage() {

        return "mktemp [-d] [TEMPLATE]";

    }

}
