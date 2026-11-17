
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe manager for background jobs and processes initiated by OM Shell.
 */
public class JobManager {

    private final Map<Integer, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger nextJobId = new AtomicInteger(1);
    private volatile long lastStartedPid = 0;

    public Job addJob(String command, Process process) {
        int id = nextJobId.getAndIncrement();
        long pid = process.pid();
        lastStartedPid = pid;

        Job job = new Job(id, pid, command, process);
        jobs.put(id, job);
        return job;
    }

    public Job addJob(String command, long pid, CompletableFuture<Integer> future) {
        int id = nextJobId.getAndIncrement();
        lastStartedPid = pid;

        Job job = new Job(id, pid, command, future);
        jobs.put(id, job);
        return job;
    }

    public Job getJob(int id) {
        return jobs.get(id);
    }

    public Job getJobByPid(long pid) {
        for (Job job : jobs.values()) {
            if (job.getPid() == pid) {
                return job;
            }
        }
        return null;
    }

    public List<Job> getAllJobs() {
        List<Job> list = new ArrayList<>(jobs.values());
        list.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        return Collections.unmodifiableList(list);
    }

    public long getLastStartedPid() {
        return lastStartedPid;
    }

    public int waitForJob(int id) throws InterruptedException {
        Job job = jobs.get(id);
        if (job == null) {
            return -1;
        }
        int code = job.waitFor();
        return code;
    }

    public int waitForPid(long pid) throws InterruptedException {
        Job job = getJobByPid(pid);
        if (job != null) {
            return job.waitFor();
        }

        // Check if there is an external OS process matching this pid
        ProcessHandle.of(pid).ifPresent(handle -> {
            try {
                handle.onExit().get();
            } catch (Exception ignored) {}
        });
        return 0;
    }

    public boolean terminateJob(int id) {
        Job job = jobs.get(id);
        if (job != null) {
            job.terminate();
            return true;
        }
        return false;
    }

    public boolean terminatePid(long pid) {
        Job job = getJobByPid(pid);
        if (job != null) {
            job.terminate();
            return true;
        }
        ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
        return true;
    }

    public void removeJob(int id) {
        jobs.remove(id);
    }

    public boolean isEmpty() {
        return jobs.isEmpty();
    }
}
