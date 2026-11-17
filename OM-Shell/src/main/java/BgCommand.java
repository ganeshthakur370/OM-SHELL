import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;



public class BgCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        PrintStream error = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        error.println("omsh: bg: resuming stopped jobs is not supported yet");

        return 1;

    }



    @Override

    public String getName() {

        return "bg";

    }



    @Override

    public String getDescription() {

        return "Resume a stopped background job (not yet supported)";

    }



    @Override

    public String getUsage() {

        return "bg [job_spec ...]";

    }

}
