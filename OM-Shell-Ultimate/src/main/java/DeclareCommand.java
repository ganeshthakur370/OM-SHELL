
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Declares indexed or associative arrays and prints their current values. */
public class DeclareCommand implements BuiltinCommand {
    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout,
                       OutputStream stderr, Shell shell) {
        PrintStream output = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        if (args.isEmpty() || args.get(0).equals("-p")) {
            for (Map.Entry<String, String> variable : shell.getVariables().entrySet()) {
                output.println("declare -- " + variable.getKey() + "=\"" + variable.getValue() + "\"");
            }
            return 0;
        }
        if (args.size() < 2 || (!args.get(0).equals("-a") && !args.get(0).equals("-A"))) {
            new PrintStream(stderr, true, StandardCharsets.UTF_8).println("usage: declare -a name=(values) | declare -A name=([key]=value)");
            return 2;
        }
        String option = args.get(0);
        String name = args.get(1);
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) return 2;
        if (option.equals("-a")) shell.setIndexedArray(name, List.of());
        else shell.setAssociativeArray(name, Map.of());
        return 0;
    }
    @Override public String getName() { return "declare"; }
    @Override public String getDescription() { return "Declare or display shell variables and arrays"; }
    @Override public String getUsage() { return "declare -a name | declare -A name | declare -p"; }
}
