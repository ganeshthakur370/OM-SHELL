import java.io.InputStream;

import java.io.OutputStream;

import java.util.List;



public class WaitCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        if (args.isEmpty()) {



            int lastCode = 0;

            for (var job : shell.getJobManager().getAllJobs()) {

                try {

                    lastCode = job.waitFor();

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    return 130;

                }

            }

            return lastCode;

        }



        int exitStatus = 0;

        for (String arg : args) {

            try {

                if (arg.startsWith("%")) {

                    int jobId = Integer.parseInt(arg.substring(1));

                    exitStatus = shell.getJobManager().waitForJob(jobId);

                } else {

                    long pid = Long.parseLong(arg);

                    exitStatus = shell.getJobManager().waitForPid(pid);

                }

            } catch (NumberFormatException e) {

                exitStatus = 127;

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                return 130;

            }

        }



        return exitStatus;

    }



    @Override

    public String getName() {

        return "wait";

    }



    @Override

    public String getDescription() {

        return "Wait for job completion and return exit status";

    }



    @Override

    public String getUsage() {

        return "wait [jobspec | pid ...]";

    }

}
