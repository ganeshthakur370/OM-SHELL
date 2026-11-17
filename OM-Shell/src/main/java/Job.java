import java.util.concurrent.CompletableFuture;



public class Job {



    public enum Status {

        RUNNING,

        STOPPED,

        DONE,

        FAILED,

        TERMINATED

    }



    private final int id;

    private final long pid;

    private final String command;

    private final Process process;

    private final CompletableFuture<Integer> future;

    private volatile Status status;

    private volatile int exitCode;

    private final long startTime;



    public Job(int id, long pid, String command, Process process) {

        this.id = id;

        this.pid = pid;

        this.command = command;

        this.process = process;

        this.future = null;

        this.status = Status.RUNNING;

        this.exitCode = 0;

        this.startTime = System.currentTimeMillis();

    }



    public Job(int id, long pid, String command, CompletableFuture<Integer> future) {

        this.id = id;

        this.pid = pid;

        this.command = command;

        this.process = null;

        this.future = future;

        this.status = Status.RUNNING;

        this.exitCode = 0;

        this.startTime = System.currentTimeMillis();

    }



    public int getId() {

        return id;

    }



    public long getPid() {

        return pid;

    }



    public String getCommand() {

        return command;

    }



    public Status getStatus() {

        checkStatus();

        return status;

    }



    public void setStatus(Status status) {

        this.status = status;

    }



    public int getExitCode() {

        return exitCode;

    }



    public long getStartTime() {

        return startTime;

    }



    public boolean isAlive() {

        checkStatus();

        return status == Status.RUNNING || status == Status.STOPPED;

    }



    public int waitFor() throws InterruptedException {

        if (process != null) {

            exitCode = process.waitFor();

            status = (exitCode == 0) ? Status.DONE : Status.FAILED;

            return exitCode;

        } else if (future != null) {

            try {

                exitCode = future.join();

                status = (exitCode == 0) ? Status.DONE : Status.FAILED;

                return exitCode;

            } catch (Exception e) {

                status = Status.FAILED;

                exitCode = 1;

                return exitCode;

            }

        }

        return exitCode;

    }



    public void resume() {

        if (status == Status.STOPPED) {

            status = Status.RUNNING;



            try {

                ProcessHandle.of(pid).ifPresent(h -> {

                    try {

                        new ProcessBuilder("kill", "-CONT", String.valueOf(pid)).start();

                    } catch (Exception ignored) {}

                });

            } catch (Exception ignored) {}

        }

    }



    public void stop() {

        if (status == Status.RUNNING) {

            status = Status.STOPPED;

            try {

                ProcessHandle.of(pid).ifPresent(h -> {

                    try {

                        new ProcessBuilder("kill", "-STOP", String.valueOf(pid)).start();

                    } catch (Exception ignored) {}

                });

            } catch (Exception ignored) {}

        }

    }



    public void terminate() {

        if (process != null && process.isAlive()) {

            process.destroyForcibly();

            status = Status.TERMINATED;

        } else if (future != null && !future.isDone()) {

            future.cancel(true);

            status = Status.TERMINATED;

        }

    }



    private void checkStatus() {

        if (status != Status.RUNNING) {

            return;

        }



        if (process != null) {

            if (!process.isAlive()) {

                exitCode = process.exitValue();

                status = (exitCode == 0) ? Status.DONE : Status.FAILED;

            }

        } else if (future != null) {

            if (future.isDone()) {

                try {

                    exitCode = future.getNow(0);

                    status = (exitCode == 0) ? Status.DONE : Status.FAILED;

                } catch (Exception e) {

                    status = Status.FAILED;

                    exitCode = 1;

                }

            }

        }

    }



    @Override

    public String toString() {

        return String.format("[%d] %-10s pid=%-6d %s", id, getStatus(), pid, command);

    }

}
