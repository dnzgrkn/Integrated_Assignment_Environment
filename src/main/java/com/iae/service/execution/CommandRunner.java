package com.iae.service.execution;

import com.iae.model.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CommandRunner {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    public static class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;

        public CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }
    }

    public CommandResult runProcess(List<String> command, Path workingDir, int timeoutSeconds) {
        if (command == null || command.isEmpty()) {
            return new CommandResult(-1, "", "Empty command", false);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return new CommandResult(-1, "", "Failed to start process: " + e.getMessage(), false);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<String> stdoutF = pool.submit(() -> readStream(process.getInputStream()));
        Future<String> stderrF = pool.submit(() -> readStream(process.getErrorStream()));

        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                pool.shutdownNow();
                return new CommandResult(-1, safeGet(stdoutF), safeGet(stderrF), true);
            }
            int exit = process.exitValue();
            String out = stdoutF.get(2, TimeUnit.SECONDS);
            String err = stderrF.get(2, TimeUnit.SECONDS);
            return new CommandResult(exit, out, err, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(-1, "", "Interrupted: " + e.getMessage(), false);
        } catch (ExecutionException | TimeoutException e) {
            return new CommandResult(-1, "", "Stream read failed: " + e.getMessage(), false);
        } finally {
            pool.shutdownNow();
        }
    }

    public CommandResult compile(Configuration config, Path sourceDir, int compileTimeoutSeconds) {
        if (!config.isCompiled()) {
            return new CommandResult(0, "", "", false);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(config.getCompilerPath());
        String flags = config.getCompilerFlags();
        if (flags != null && !flags.isBlank()) {
            for (String token : flags.trim().split("\\s+")) {
                cmd.add(token);
            }
        }
        cmd.add(config.getExpectedSourceFileName());
        return runProcess(cmd, sourceDir, compileTimeoutSeconds);
    }

    public CommandResult execute(Configuration config, Path workingDir, String argumentsLine, int runTimeoutSeconds) {
        List<String> cmd = new ArrayList<>();
        String runCommand = config.getRunCommand();
        if (runCommand == null || runCommand.isBlank()) {
            return new CommandResult(-1, "", "No run command configured", false);
        }
        for (String token : runCommand.trim().split("\\s+")) {
            cmd.add(adaptForOs(token));
        }
        if (argumentsLine != null && !argumentsLine.isBlank()) {
            for (String token : argumentsLine.trim().split("\\s+")) {
                cmd.add(token);
            }
        }
        return runProcess(cmd, workingDir, runTimeoutSeconds);
    }

    // On Windows, "./main" should resolve to "main.exe" in the working dir.
    private static String adaptForOs(String token) {
        if (!WINDOWS) {
            return token;
        }
        if (token.startsWith("./")) {
            String name = token.substring(2);
            return name.contains(".") ? name : name + ".exe";
        }
        return token;
    }

    private static String readStream(InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // Stream closed early — return what we have.
        }
        return sb.toString();
    }

    private static String safeGet(Future<String> f) {
        try {
            return f.get(500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return "";
        }
    }
}
