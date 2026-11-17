import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.List;



public class JobsCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);



        boolean longFormat = false;

        boolean pidsOnly = false;

        boolean runningOnly = false;

        boolean stoppedOnly = false;

        List<Integer> targetIds = new ArrayList<>();



        for (String arg : args) {

            if (arg.equals("-l")) {

                longFormat = true;

            } else if (arg.equals("-p")) {

                pidsOnly = true;

            } else if (arg.equals("-r")) {

                runningOnly = true;

            } else if (arg.equals("-s")) {

                stoppedOnly = true;

            } else if (arg.startsWith("%") || arg.matches("\\d+")) {

                try {

                    String num = arg.startsWith("%") ? arg.substring(1) : arg;

                    targetIds.add(Integer.parseInt(num));

                } catch (NumberFormatException ignored) {}

            }

        }



        List<Job> allJobs = shell.getJobManager().getAllJobs();

        for (Job job : allJobs) {

            if (!targetIds.isEmpty() && !targetIds.contains(job.getId())) {

                continue;

            }

            Job.Status st = job.getStatus();

            if (runningOnly && st != Job.Status.RUNNING) {

                continue;

            }

            if (stoppedOnly && st != Job.Status.STOPPED) {

                continue;

            }



            if (pidsOnly) {

                out.println(job.getPid());

            } else if (longFormat) {

                out.printf("[%d]  %d %-10s %s%n", job.getId(), job.getPid(), st, job.getCommand());

            } else {

                out.printf("[%d]  %-10s %s%n", job.getId(), st, job.getCommand());

            }

        }



        return 0;

    }



    @Override

    public String getName() {

        return "jobs";

    }



    @Override

    public String getDescription() {

        return "List active background jobs with status and process information";

    }



    @Override

    public String getUsage() {

        return "jobs [-l|-p|-r|-s] [%job_id...]";

    }

}
