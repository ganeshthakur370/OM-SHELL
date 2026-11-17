
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Defines or displays aliases.
 */
public class AliasCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        if (args.isEmpty()) {
            Map<String, String> aliases = shell.getAliases();
            for (Map.Entry<String, String> entry : aliases.entrySet()) {
                out.printf("alias %s='%s'%n", entry.getKey(), entry.getValue());
            }
            return 0;
        }

        for (String arg : args) {
            int equalsIdx = arg.indexOf('=');
            if (equalsIdx > 0) {
                String name = arg.substring(0, equalsIdx);
                String value = arg.substring(equalsIdx + 1);
                if (value.length() >= 2 && ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\"")))) {
                    value = value.substring(1, value.length() - 1);
                }
                shell.setAlias(name, value);
            } else {
                String existing = shell.getAlias(arg);
                if (existing != null) {
                    out.printf("alias %s='%s'%n", arg, existing);
                } else {
                    PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);
                    err.printf("omsh: alias: %s: not found%n", arg);
                    return 1;
                }
            }
        }

        return 0;
    }

    @Override
    public String getName() {
        return "alias";
    }

    @Override
    public String getDescription() {
        return "Define or display aliases";
    }

    @Override
    public String getUsage() {
        return "alias [name[=value] ...]";
    }
}
