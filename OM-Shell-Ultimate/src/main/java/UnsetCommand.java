import java.io.InputStream;

import java.io.OutputStream;

import java.util.List;



public class UnsetCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        for (String varName : args) {

            int bracket = varName.indexOf('[');

            if (bracket > 0 && varName.endsWith("]")) {

                shell.removeArrayElement(varName.substring(0, bracket),

                        varName.substring(bracket + 1, varName.length() - 1));

            } else {

                shell.removeVariable(varName);

            }

        }

        return 0;

    }



    @Override

    public String getName() {

        return "unset";

    }



    @Override

    public String getDescription() {

        return "Unset values and attributes of shell variables";

    }



    @Override

    public String getUsage() {

        return "unset [name ...]";

    }

}
