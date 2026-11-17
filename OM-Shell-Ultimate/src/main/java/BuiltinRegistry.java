
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry holding all available in-process built-in commands.
 */
public class BuiltinRegistry {

    private final Map<String, BuiltinCommand> commands = new LinkedHashMap<>();

    public BuiltinRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        register(new HelpCommand());
        register(new CdCommand());
        register(new PwdCommand());
        register(new EchoCommand());
        register(new PrintfCommand());
        register(new CatCommand());
        register(new MkdirCommand());
        register(new TouchCommand());
        register(new RmCommand());
        register(new HistoryCommand());
        register(new ClearCommand());
        register(new ExitCommand());
        register(new ExportCommand());
        register(new DeclareCommand());
        register(new UnsetCommand());
        register(new EnvCommand());
        register(new AliasCommand());
        register(new UnaliasCommand());
        register(new TypeCommand());
        register(new WhichCommand());
        register(new JobsCommand());
        register(new FgCommand());
        register(new BgCommand());
        register(new KillCommand());
        register(new SourceCommand());
        register(new TrueCommand());
        register(new FalseCommand());
        register(new TestCommand(false)); // test
        register(new TestCommand(true));  // [
        register(new SleepCommand());
        register(new WaitCommand());
        register(new MktempCommand());
        register(new GrepCommand());
        register(new WcCommand());
        register(new TrCommand());
        register(new TrapCommand());
        register(new SetCommand());
        register(new ShoptCommand());

        // Aliases / alternate names for built-ins
        BuiltinCommand sourceCmd = commands.get("source");
        if (sourceCmd != null) {
            commands.put(".", sourceCmd);
        }
        BuiltinCommand killCmd = commands.get("kill");
        if (killCmd != null) {
            commands.put("stop", killCmd);
        }
    }

    public void register(BuiltinCommand command) {
        commands.put(command.getName(), command);
    }

    public boolean isBuiltin(String name) {
        if (name == null) return false;
        return commands.containsKey(name);
    }

    public BuiltinCommand get(String name) {
        if (name == null) return null;
        return commands.get(name);
    }

    public Collection<BuiltinCommand> getAll() {
        return Collections.unmodifiableCollection(commands.values());
    }
}
