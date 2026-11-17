import java.io.InputStream;

import java.io.OutputStream;

import java.util.List;



public class FalseCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        return 1;

    }



    @Override

    public String getName() {

        return "false";

    }



    @Override

    public String getDescription() {

        return "Return an unsuccessful result (1)";

    }



    @Override

    public String getUsage() {

        return "false";

    }

}
