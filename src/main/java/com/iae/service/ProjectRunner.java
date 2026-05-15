package com.iae.service;

import com.iae.model.ComparisonResult;
import com.iae.model.Configuration;
import com.iae.model.Project;
import com.iae.model.RunResult;
import com.iae.service.compare.OutputComparator;
import com.iae.service.execution.CommandRunner;
import com.iae.service.execution.CommandRunner.CommandResult;
import com.iae.service.extract.ExtractionException;
import com.iae.service.extract.ZipExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class ProjectRunner {

    public interface RunListener {
        void onSubmissionStarted(String studentId);
        void onSubmissionCompleted(RunResult result);
        void onProgress(int completed, int total);
        void onAllCompleted();
    }

    private static final int DEFAULT_COMPILE_TIMEOUT = 30;
    private static final int DEFAULT_RUN_TIMEOUT = 10;

    private final Project project;
    private final int compileTimeoutSeconds;
    private final int runTimeoutSeconds;
    private final List<RunListener> listeners = new ArrayList<>();

    private final ZipExtractor extractor = new ZipExtractor();
    private final CommandRunner commandRunner = new CommandRunner();
    private final OutputComparator comparator = new OutputComparator();

    public ProjectRunner(Project project) {
        this(project, DEFAULT_COMPILE_TIMEOUT, DEFAULT_RUN_TIMEOUT);
    }

    public ProjectRunner(Project project, int compileTimeoutSeconds, int runTimeoutSeconds) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }
        if (project.getActiveConfiguration() == null) {
            throw new IllegalArgumentException("project has no active configuration");
        }
        this.project = project;
        this.compileTimeoutSeconds = compileTimeoutSeconds;
        this.runTimeoutSeconds = runTimeoutSeconds;
    }

    public void addListener(RunListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(RunListener listener) {
        listeners.remove(listener);
    }

    public List<RunResult> runAll() throws IOException {
        project.clearResults();
        List<RunResult> results = new ArrayList<>();
        List<Path> zips = listZips();
        int total = zips.size();

        for (int i = 0; i < total; i++) {
            Path zip = zips.get(i);
            String studentId = stripZipExtension(zip.getFileName().toString());
            notifyStarted(studentId);

            RunResult result;
            try {
                result = processSingleSubmission(studentId, zip);
            } catch (Throwable t) {
                result = RunResult.runtimeError(studentId, null,
                        "Unexpected error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }

            results.add(result);
            project.addResult(result);
            notifyCompleted(result);
            notifyProgress(i + 1, total);
        }

        notifyAllCompleted();
        return results;
    }

    private RunResult processSingleSubmission(String studentId, Path zip) throws IOException {
        Configuration config = project.getActiveConfiguration();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("iae-" + studentId + "-");

            try {
                extractor.extract(zip, workDir);
            } catch (ExtractionException e) {
                return RunResult.compileError(studentId, "Extraction failed: " + e.getMessage());
            }

            String expectedFile = config.getExpectedSourceFileName();
            if (!extractor.validateStructure(workDir, expectedFile)) {
                return RunResult.compileError(studentId,
                        "Source file " + expectedFile + " not found in submission");
            }

            Path sourceDir = findSourceDir(workDir, expectedFile).orElse(workDir);

            if (config.isCompiled()) {
                CommandResult compileResult = commandRunner.compile(config, sourceDir, compileTimeoutSeconds);
                if (compileResult.timedOut) {
                    return RunResult.compileError(studentId, "Compilation timed out");
                }
                if (compileResult.exitCode != 0) {
                    return RunResult.compileError(studentId, compileResult.stderr);
                }
            }

            CommandResult execResult = commandRunner.execute(
                    config, sourceDir, project.getCommandLineArguments(), runTimeoutSeconds);
            if (execResult.timedOut) {
                return RunResult.timeout(studentId,
                        "Process exceeded " + runTimeoutSeconds + "s limit");
            }
            if (execResult.exitCode != 0) {
                return RunResult.runtimeError(studentId, execResult.stdout, execResult.stderr);
            }

            ComparisonResult cmp = comparator.compare(execResult.stdout, project.getExpectedOutput());
            if (cmp.isMatch()) {
                return RunResult.pass(studentId, execResult.stdout);
            }
            String details = "Expected line " + cmp.getLineNumber() + ": "
                    + cmp.getExpected() + " | got: " + cmp.getActual();
            return RunResult.fail(studentId, execResult.stdout, details);

        } finally {
            if (workDir != null) {
                cleanupQuietly(workDir);
            }
        }
    }

    private List<Path> listZips() throws IOException {
        String dirPath = project.getSubmissionsDirectoryPath();
        if (dirPath == null || dirPath.isBlank()) {
            return List.of();
        }
        Path dir = Paths.get(dirPath);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
                    .sorted()
                    .toList();
        }
    }

    private static Optional<Path> findSourceDir(Path root, String fileName) {
        try (Stream<Path> walk = Files.walk(root, 3)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .map(Path::getParent)
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String stripZipExtension(String name) {
        int dot = name.toLowerCase().lastIndexOf(".zip");
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    private static void cleanupQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private void notifyStarted(String studentId) {
        for (RunListener l : listeners) l.onSubmissionStarted(studentId);
    }

    private void notifyCompleted(RunResult r) {
        for (RunListener l : listeners) l.onSubmissionCompleted(r);
    }

    private void notifyProgress(int completed, int total) {
        for (RunListener l : listeners) l.onProgress(completed, total);
    }

    private void notifyAllCompleted() {
        for (RunListener l : listeners) l.onAllCompleted();
    }
}
