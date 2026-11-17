import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class JobControlSmokeTest {

    public static void main(String[] args) throws Exception {
        Shell shell = new Shell();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        shell.setStandardOutput(new PrintStream(captured, true, StandardCharsets.UTF_8));
        JobManager jm = shell.getJobManager();

        
        CompletableFuture<Integer> fut1 = new CompletableFuture<>();
        CompletableFuture<Integer> fut2 = new CompletableFuture<>();
        CompletableFuture<Integer> fut3 = new CompletableFuture<>();

        Job job1 = jm.addJob("sleep 100", 101, fut1);
        Job job2 = jm.addJob("worker_task", 102, fut2);
        Job job3 = jm.addJob("stopped_proc", 103, fut3);

        check(jm.getAllJobs().size() == 3, "initial three jobs tracked");
        check(jm.getAllJobs().size() == 3, "three running jobs");

        
        jm.stopJob(job3.getId());
        check(job3.getStatus() == Job.Status.STOPPED, "job3 stopped status");
        check(jm.getStoppedJobs().size() == 1, "one stopped job");
        check(jm.getRunningJobs().size() == 2, "two running jobs");

        
        jm.resumeInBackground(job3.getId());
        check(job3.getStatus() == Job.Status.RUNNING, "job3 resumed running status");

        
        captured.reset();
        shell.executeLine("jobs");
        String outNormal = captured.toString(StandardCharsets.UTF_8);
        check(outNormal.contains("[1]"), "jobs lists [1]");
        check(outNormal.contains("[2]"), "jobs lists [2]");
        check(outNormal.contains("[3]"), "jobs lists [3]");

        captured.reset();
        shell.executeLine("jobs -p");
        String outPids = captured.toString(StandardCharsets.UTF_8);
        check(outPids.contains("101") && outPids.contains("102") && outPids.contains("103"), "jobs -p lists pids only");

        captured.reset();
        shell.executeLine("jobs -l");
        String outLong = captured.toString(StandardCharsets.UTF_8);
        check(outLong.contains("101") && outLong.contains("RUNNING"), "jobs -l has long format");

        
        shell.executeLine("disown %2");
        check(jm.getJob(job2.getId()) == null, "disown %2 removed job 2");
        check(jm.getAllJobs().size() == 2, "two jobs remaining after disown");

        shell.executeLine("disown -a");
        check(jm.getAllJobs().isEmpty(), "disown -a cleared all jobs");

        
        fut1.complete(0);
        fut2.complete(0);
        fut3.complete(0);

        System.out.println("JobControlSmokeTest: all checks passed successfully!");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("JobControlSmokeTest FAILED: " + description);
        }
    }
}
