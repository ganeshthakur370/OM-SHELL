
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Moves a background job into the foreground and waits for its completion.
 */
public class FgCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        List<Job> allJobs = shell.getJobManager().getAllJobs();
        if (allJobs.isEmpty()) {
            err.println("omsh: fg: no current jobs");
            return 1;
        }

        Job targetJob = null;
        if (args.isEmpty()) {
            // Select most recent job
            targetJob = allJobs.get(allJobs.size() - 1);
        } else {
            String arg = args.get(0).replace("%", "");
            try {
                int jobId = Integer.parseInt(arg);
                targetJob = shell.getJobManager().getJob(jobId);
            } catch (NumberFormatException e) {
                err.println("omsh: fg: " + args.get(0) + ": no such job");
                return 1;
            }
        }

        if (targetJob == null) {
            err.println("omsh: fg: no such job");
            return 1;
        }

        out.println(targetJob.getCommand());
        try {
            int exitCode = targetJob.waitFor();
            shell.getJobManager().removeJob(targetJob.getId());
            return exitCode;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("omsh: fg: interrupted");
            return 130;
        }
    }

    @Override
    public String getName() {
        return "fg";
    }

    @Override
    public String getDescription() {
        return "Move jobs to the foreground";
    }

    @Override
    public String getUsage() {
        return "fg [job_spec]";
    }
}
