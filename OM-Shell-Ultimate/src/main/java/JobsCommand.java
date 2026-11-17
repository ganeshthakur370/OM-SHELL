
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Lists the active and completed background jobs.
 */
public class JobsCommand implements BuiltinCommand {

    @Override
    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {
        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        List<Job> jobs = shell.getJobManager().getAllJobs();

        for (Job job : jobs) {
            out.println(job.toString());
        }

        return 0;
    }

    @Override
    public String getName() {
        return "jobs";
    }

    @Override
    public String getDescription() {
        return "List active background jobs";
    }

    @Override
    public String getUsage() {
        return "jobs";
    }
}
