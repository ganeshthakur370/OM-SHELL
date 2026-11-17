import java.util.ArrayList;

import java.util.Collections;

import java.util.List;

import java.util.Map;

import java.util.concurrent.CompletableFuture;

import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.atomic.AtomicInteger;



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



    public List<Job> getRunningJobs() {

        List<Job> list = new ArrayList<>();

        for (Job job : getAllJobs()) {

            if (job.getStatus() == Job.Status.RUNNING) {

                list.add(job);

            }

        }

        return list;

    }



    public List<Job> getStoppedJobs() {

        List<Job> list = new ArrayList<>();

        for (Job job : getAllJobs()) {

            if (job.getStatus() == Job.Status.STOPPED) {

                list.add(job);

            }

        }

        return list;

    }



    public long getLastStartedPid() {

        return lastStartedPid;

    }



    public int waitForJob(int id) throws InterruptedException {

        Job job = jobs.get(id);

        if (job == null) {

            return -1;

        }

        return job.waitFor();

    }



    public int waitForPid(long pid) throws InterruptedException {

        Job job = getJobByPid(pid);

        if (job != null) {

            return job.waitFor();

        }



        ProcessHandle.of(pid).ifPresent(handle -> {

            try {

                handle.onExit().get();

            } catch (Exception ignored) {}

        });

        return 0;

    }



    public boolean resumeInBackground(int id) {

        Job job = jobs.get(id);

        if (job != null) {

            job.resume();

            return true;

        }

        return false;

    }



    public boolean stopJob(int id) {

        Job job = jobs.get(id);

        if (job != null) {

            job.stop();

            return true;

        }

        return false;

    }



    public boolean disownJob(int id) {

        return jobs.remove(id) != null;

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



    public void cleanDoneJobs() {

        jobs.entrySet().removeIf(e -> !e.getValue().isAlive());

    }



    public boolean isEmpty() {

        return jobs.isEmpty();

    }

}
