import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.List;



public class HelpCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);



        if (!args.isEmpty()) {

            String topic = args.get(0);

            BuiltinCommand cmd = shell.getBuiltinRegistry().get(topic);

            if (cmd != null) {

                out.println(cmd.getName() + ": " + cmd.getUsage());

                out.println("    " + cmd.getDescription());

                return 0;

            } else {

                PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

                err.println("omsh: help: no help topics match `" + topic + "'");

                return 1;

            }

        }



        out.println("OM Shell (omsh) - Pure Java Interactive Shell");

        out.println("These shell commands are defined internally. Type `help name' to find out more.");

        out.println();



        List<BuiltinCommand> commands = new ArrayList<>(shell.getBuiltinRegistry().getAll());

        commands.sort(Comparator.comparing(BuiltinCommand::getName));



        for (BuiltinCommand cmd : commands) {

            out.printf("  %-12s %-24s %s%n", cmd.getName(), cmd.getUsage(), cmd.getDescription());

        }



        out.println();

        out.println("Use `type [command]' or `which [command]' to inspect command resolution.");

        return 0;

    }



    @Override

    public String getName() {

        return "help";

    }



    @Override

    public String getDescription() {

        return "Display information about builtin commands";

    }



    @Override

    public String getUsage() {

        return "help [pattern ...]";

    }

}
