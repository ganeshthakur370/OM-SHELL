import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.List;



public class KillCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        if (args.isEmpty()) {

            err.println("omsh: kill: usage: kill [-s sigspec | -n signum | -sigspec] pid | jobspec ... or kill -l [sigspec]");

            return 1;

        }



        int status = 0;

        for (String arg : args) {

            if (arg.startsWith("-")) {

                continue;

            }



            if (arg.startsWith("%")) {

                try {

                    int jobId = Integer.parseInt(arg.substring(1));

                    if (!shell.getJobManager().terminateJob(jobId)) {

                        err.println("omsh: kill: " + arg + ": no such job");

                        status = 1;

                    }

                } catch (NumberFormatException e) {

                    err.println("omsh: kill: " + arg + ": invalid job spec");

                    status = 1;

                }

            } else {

                try {

                    long pid = Long.parseLong(arg);

                    if (!shell.getJobManager().terminatePid(pid)) {

                        err.println("omsh: kill: (" + pid + ") - No such process");

                        status = 1;

                    }

                } catch (NumberFormatException e) {

                    err.println("omsh: kill: " + arg + ": arguments must be process or job IDs");

                    status = 1;

                }

            }

        }



        return status;

    }



    @Override

    public String getName() {

        return "kill";

    }



    @Override

    public String getDescription() {

        return "Send a signal to a job or process";

    }



    @Override

    public String getUsage() {

        return "kill [-s sigspec] pid | jobspec ...";

    }

}
