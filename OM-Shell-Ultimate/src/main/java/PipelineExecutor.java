
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates execution of single commands and multi-stage pipelines.
 * Connects standard streams, handles file redirections, and mixes
 * built-in commands with external processes seamlessly.
 */
public class PipelineExecutor {

    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "om-pipeline-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final Shell shell;

    public PipelineExecutor(Shell shell) {
        this.shell = shell;
    }

    /**
     * Executes a CommandLine (pipeline or single command), optionally backgrounded.
     *
     * @param commandLine The parsed command line
     * @return Exit status code of the pipeline
     */
    public int execute(CommandLine commandLine) {
        if (commandLine == null || commandLine.isEmpty()) {
            return 0;
        }

        List<PipelineStage> stages = commandLine.getStages();

        if (commandLine.isBackground()) {
            return executeBackground(stages);
        } else {
            return executeForeground(stages);
        }
    }

    private int executeForeground(List<PipelineStage> stages) {
        if (stages.size() == 1) {
            PipelineStage stage = stages.get(0);
            String cmdName = stage.getCommandName();

            // Check if it's a shell function
            if (shell.hasFunction(cmdName)) {
                return executeFunction(stage, shell.getStandardInput(), shell.getStandardOutput(), shell.getStandardError());
            }

            // Check if it's a builtin
            if (shell.getBuiltinRegistry().isBuiltin(cmdName)) {
                return executeSingleBuiltin(stage);
            }
        }

        // Multi-stage pipeline or external command
        return executePipelineStages(stages);
    }

    private int executeBackground(List<PipelineStage> stages) {
        String fullCmd = formatStages(stages);
        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> executeForeground(stages), THREAD_POOL);
        long dummyPid = ProcessHandle.current().pid() + (long) (Math.random() * 1000) + 1;
        Job job = shell.getJobManager().addJob(fullCmd, dummyPid, future);
        System.out.printf("[%d] %d%n", job.getId(), job.getPid());
        return 0;
    }

    private int executeSingleBuiltin(PipelineStage stage) {
        StreamsHolder streams = null;
        try {
            streams = setupStreams(stage, shell.getStandardInput(), shell.getStandardOutput(), shell.getStandardError());
            BuiltinCommand builtin = shell.getBuiltinRegistry().get(stage.getCommandName());
            return builtin.execute(stage.getArguments(), streams.in, streams.out, streams.err, shell);
        } catch (Exception e) {
            shell.getStandardError().println("omsh: " + stage.getCommandName() + ": " + e.getMessage());
            return 1;
        } finally {
            if (streams != null) {
                streams.close();
            }
        }
    }

    private int executeFunction(PipelineStage stage, InputStream in, OutputStream out, OutputStream err) {
        List<String> bodyLines = shell.getFunction(stage.getCommandName());
        if (bodyLines == null) return 1;

        List<String> oldPositional = shell.getPositionalParameters();
        shell.setPositionalParameters(stage.getArguments());

        int status = 0;
        try {
            for (String line : bodyLines) {
                status = shell.executeLine(line);
            }
        } catch (Exception e) {
            shell.getStandardError().println("omsh: " + stage.getCommandName() + ": " + e.getMessage());
            status = 1;
        } finally {
            shell.setPositionalParameters(oldPositional);
        }
        return status;
    }

    private int executePipelineStages(List<PipelineStage> stages) {
        int n = stages.size();
        List<InputStream> stageInputs = new ArrayList<>(n);
        List<OutputStream> stageOutputs = new ArrayList<>(n);
        List<StreamsHolder> holdersToClose = new ArrayList<>();

        // Create piped streams between stages
        PipedOutputStream prevPipedOut = null;

        for (int i = 0; i < n; i++) {
            PipelineStage stage = stages.get(i);
            InputStream in;
            OutputStream out;
            OutputStream err = shell.getStandardError();

            // Set up input
            if (i == 0) {
                in = shell.getStandardInput();
            } else {
                try {
                    in = new PipedInputStream(prevPipedOut, 65536);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create pipeline pipe: " + e.getMessage(), e);
                }
            }

            // Set up output
            if (i == n - 1) {
                out = shell.getStandardOutput();
            } else {
                try {
                    PipedOutputStream pipedOut = new PipedOutputStream();
                    out = pipedOut;
                    prevPipedOut = pipedOut;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create output pipe: " + e.getMessage(), e);
                }
            }

            // Apply redirections for this stage
            StreamsHolder streams = setupStreams(stage, in, out, err);
            holdersToClose.add(streams);
            stageInputs.add(streams.in);
            stageOutputs.add(streams.out);
        }

        // Launch all stages concurrently
        List<CompletableFuture<Integer>> stageFutures = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            final int index = i;
            PipelineStage stage = stages.get(index);
            InputStream in = stageInputs.get(index);
            OutputStream out = stageOutputs.get(index);
            OutputStream err = holdersToClose.get(index).err;

            CompletableFuture<Integer> stageFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return runStage(stage, in, out, err);
                } finally {
                    try {
                        if (index < n - 1) {
                            out.flush();
                            out.close();
                        }
                    } catch (IOException ignored) {}
                }
            }, THREAD_POOL);

            stageFutures.add(stageFuture);
        }

        // Wait for all stages to complete
        int lastStatus = 0;
        for (int i = 0; i < n; i++) {
            try {
                int status = stageFutures.get(i).join();
                if (i == n - 1) {
                    lastStatus = status;
                }
            } catch (Exception e) {
                if (i == n - 1) {
                    lastStatus = 1;
                }
            }
        }

        // Close file redirection streams
        for (StreamsHolder holder : holdersToClose) {
            holder.close();
        }

        return lastStatus;
    }

    private int runStage(PipelineStage stage, InputStream in, OutputStream out, OutputStream err) {
        String cmdName = stage.getCommandName();

        // 1. Check if shell function
        if (shell.hasFunction(cmdName)) {
            return executeFunction(stage, in, out, err);
        }

        // 2. Check if builtin
        if (shell.getBuiltinRegistry().isBuiltin(cmdName)) {
            try {
                BuiltinCommand builtin = shell.getBuiltinRegistry().get(cmdName);
                return builtin.execute(stage.getArguments(), in, out, err, shell);
            } catch (Exception e) {
                PrintStream perr = new PrintStream(err, true);
                perr.println("omsh: " + cmdName + ": " + e.getMessage());
                return 1;
            }
        }

        // 3. External process
        Path executable = ProcessLauncher.resolveExecutable(cmdName, shell);
        if (executable == null) {
            PrintStream perr = new PrintStream(err, true);
            perr.println("omsh: " + cmdName + ": command not found");
            return 127;
        }

        List<String> commandList = new ArrayList<>();
        commandList.add(executable.toAbsolutePath().toString());
        commandList.addAll(stage.getArguments());

        try {
            ProcessBuilder pb = ProcessLauncher.createProcessBuilder(commandList, shell);
            Process process = pb.start();

            // Pump in -> process.stdin
            CompletableFuture<Void> inPump = CompletableFuture.runAsync(() -> {
                try (OutputStream pStdin = process.getOutputStream()) {
                    if (in != null && in != System.in) {
                        in.transferTo(pStdin);
                    }
                } catch (IOException ignored) {}
            }, THREAD_POOL);

            // Pump process.stdout -> out
            CompletableFuture<Void> outPump = CompletableFuture.runAsync(() -> {
                try (InputStream pStdout = process.getInputStream()) {
                    pStdout.transferTo(out);
                    out.flush();
                } catch (IOException ignored) {}
            }, THREAD_POOL);

            // Pump process.stderr -> err
            CompletableFuture<Void> errPump = CompletableFuture.runAsync(() -> {
                try (InputStream pStderr = process.getErrorStream()) {
                    pStderr.transferTo(err);
                    err.flush();
                } catch (IOException ignored) {}
            }, THREAD_POOL);

            int exitCode = process.waitFor();
            CompletableFuture.allOf(inPump, outPump, errPump).join();
            return exitCode;
        } catch (Exception e) {
            PrintStream perr = new PrintStream(err, true);
            perr.println("omsh: " + cmdName + ": " + e.getMessage());
            return 126;
        }
    }

    private StreamsHolder setupStreams(PipelineStage stage, InputStream defaultIn, OutputStream defaultOut, OutputStream defaultErr) {
        InputStream in = defaultIn;
        OutputStream out = defaultOut;
        OutputStream err = defaultErr;
        List<Closeable> toClose = new ArrayList<>();

        for (Redirection redir : stage.getRedirections()) {
            String target = redir.getTarget();

            try {
                switch (redir.getType()) {
                    case INPUT -> {
                        InputStream fin = openInput(target);
                        in = fin;
                        toClose.add(fin);
                    }
                    case OUTPUT_OVERWRITE -> {
                        OutputStream fout = openOutput(target, false);
                        out = fout;
                        toClose.add(fout);
                    }
                    case OUTPUT_APPEND -> {
                        OutputStream fout = openOutput(target, true);
                        out = fout;
                        toClose.add(fout);
                    }
                    case ERROR_OVERWRITE -> {
                        OutputStream ferr = openOutput(target, false);
                        err = ferr;
                        toClose.add(ferr);
                    }
                    case ERROR_APPEND -> {
                        OutputStream ferr = openOutput(target, true);
                        err = ferr;
                        toClose.add(ferr);
                    }
                    case ERROR_TO_OUTPUT -> {
                        err = out;
                    }
                    case ALL_OVERWRITE -> {
                        OutputStream fall = openOutput(target, false);
                        out = fall;
                        err = fall;
                        toClose.add(fall);
                    }
                    case ALL_APPEND -> {
                        OutputStream fall = openOutput(target, true);
                        out = fall;
                        err = fall;
                        toClose.add(fall);
                    }
                }
            } catch (Exception e) {
                shell.getStandardError().println("omsh: cannot redirect " + redir.getType() + " to '" + target + "': " + e.getMessage());
            }
        }

        return new StreamsHolder(in, out, err, toClose);
    }

    private InputStream openInput(String target) throws IOException {
        if (target.equals("/dev/null") || target.equalsIgnoreCase("nul")) {
            return InputStream.nullInputStream();
        }
        Path path = shell.resolvePath(target);
        return Files.newInputStream(path);
    }

    private OutputStream openOutput(String target, boolean append) throws IOException {
        if (target.equals("/dev/null") || target.equalsIgnoreCase("nul")) {
            return OutputStream.nullOutputStream();
        }
        Path path = shell.resolvePath(target);
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        if (append) {
            return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            return Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
    }

    private String formatStages(List<PipelineStage> stages) {
        List<String> stageStrs = new ArrayList<>();
        for (PipelineStage stage : stages) {
            stageStrs.add(String.join(" ", stage.getWords()));
        }
        return String.join(" | ", stageStrs);
    }

    private static class StreamsHolder {
        final InputStream in;
        final OutputStream out;
        final OutputStream err;
        final List<Closeable> toClose;

        StreamsHolder(InputStream in, OutputStream out, OutputStream err, List<Closeable> toClose) {
            this.in = in;
            this.out = out;
            this.err = err;
            this.toClose = toClose;
        }

        void close() {
            for (Closeable c : toClose) {
                try {
                    c.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
