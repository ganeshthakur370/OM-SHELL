import java.io.InputStream;

import java.io.OutputStream;

import java.util.List;



public class UnaliasCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        if (args.isEmpty()) {

            return 0;

        }



        if (args.get(0).equals("-a")) {

            shell.clearAliases();

            return 0;

        }



        for (String name : args) {

            shell.removeAlias(name);

        }



        return 0;

    }



    @Override

    public String getName() {

        return "unalias";

    }



    @Override

    public String getDescription() {

        return "Remove alias definitions";

    }



    @Override

    public String getUsage() {

        return "unalias [-a] name ...";

    }

}
