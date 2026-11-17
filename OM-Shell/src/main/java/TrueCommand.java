
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;


public class TrueCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        return 0;
    }

    @Override
    public String getName() {
        return "true";
    }

    @Override
    public String getDescription() {
        return "Return a successful result (0)";
    }

    @Override
    public String getUsage() {
        return "true";
    }
}
