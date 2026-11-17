import java.io.InputStream;

import java.io.OutputStream;

import java.util.List;



public class EvalCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        if (args.isEmpty()) {

            return 0;

        }



        String commandString = String.join(" ", args);

        return shell.executeLine(commandString);

    }



    @Override

    public String getName() {

        return "eval";

    }



    @Override

    public String getDescription() {

        return "Construct and execute commands by concatenating arguments";

    }



    @Override

    public String getUsage() {

        return "eval [arg ...]";

    }

}
