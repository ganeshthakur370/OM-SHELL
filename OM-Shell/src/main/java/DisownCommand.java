import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.List;



public class DisownCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        boolean all = false;

        boolean runningOnly = false;

        List<Integer> targetIds = new ArrayList<>();



        for (String arg : args) {

            if (arg.equals("-a")) {

                all = true;

            } else if (arg.equals("-r")) {

                runningOnly = true;

            } else if (arg.startsWith("%") || arg.matches("\\d+")) {

                try {

                    String num = arg.startsWith("%") ? arg.substring(1) : arg;

                    targetIds.add(Integer.parseInt(num));

                } catch (NumberFormatException ignored) {}

            } else {

                err.println("omsh: disown: " + arg + ": invalid option or argument");

                return 1;

            }

        }



        JobManager jm = shell.getJobManager();

        if (all) {

            List<Job> jobs = runningOnly ? jm.getRunningJobs() : jm.getAllJobs();

            for (Job j : jobs) {

                jm.disownJob(j.getId());

            }

            return 0;

        }



        if (targetIds.isEmpty()) {



            List<Job> jobs = runningOnly ? jm.getRunningJobs() : jm.getAllJobs();

            if (!jobs.isEmpty()) {

                jm.disownJob(jobs.get(jobs.size() - 1).getId());

            }

            return 0;

        }



        int exitCode = 0;

        for (int id : targetIds) {

            if (!jm.disownJob(id)) {

                err.println("omsh: disown: %" + id + ": no such job");

                exitCode = 1;

            }

        }



        return exitCode;

    }



    @Override

    public String getName() {

        return "disown";

    }



    @Override

    public String getDescription() {

        return "Remove jobs from current active job table";

    }



    @Override

    public String getUsage() {

        return "disown [-a] [-r] [%job_id...]";

    }

}
