package com.iae.service;

import com.iae.model.Configuration;
import com.iae.model.LanguageType;
import com.iae.model.Project;
import com.iae.model.RunResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Behavioural tests for {@link ProjectRunner}'s batch pipeline.
 * <p>
 * These tests were previously skipped on Windows because the fixtures relied on
 * {@code sh} shell scripts, which do not exist on Windows. They now use a Python
 * interpreter instead: {@code python}/{@code python3} is available on every
 * platform the IAE targets (Python is one of the three reference configurations,
 * and the design assumes interpreters referenced by a configuration are reachable
 * from PATH -- Section 2.3 / assumption A4). As a result these tests run
 * identically on Windows, Linux and macOS rather than being skipped on Windows.
 * <p>
 * The single remaining guard is {@link #PYTHON} availability: if no Python
 * interpreter is on PATH the interpreter-driven tests are skipped (not failed).
 */
class ProjectRunnerTest {

    /** Resolved Python launcher, or {@code null} if none is available on PATH. */
    private static String PYTHON;

    @BeforeAll
    static void resolvePython() {
        PYTHON = detectPython();
    }

    @Test
    void passesAndFailsAreReportedPerSubmission(@TempDir Path tmp) throws IOException {
        assumeTrue(PYTHON != null, "no Python interpreter on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));

        zipScript(subs.resolve("20240001.zip"), "print('hello')");
        zipScript(subs.resolve("20240002.zip"), "print('wrong')");
        zipScript(subs.resolve("20240003.zip"), "import sys; sys.exit(1)");

        Project project = buildProject(subs, "hello\n");
        ProjectRunner runner = new ProjectRunner(project, 30, 5);

        List<RunResult> results = runner.runAll();

        assertEquals(3, results.size());
        assertEquals(RunResult.Status.PASS, byId(results, "20240001").getStatus());
        assertEquals(RunResult.Status.FAIL, byId(results, "20240002").getStatus());
        assertEquals(RunResult.Status.RUNTIME_ERROR, byId(results, "20240003").getStatus());
    }

    @Test
    void corruptZipDoesNotKillBatch(@TempDir Path tmp) throws IOException {
        assumeTrue(PYTHON != null, "no Python interpreter on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));

        zipScript(subs.resolve("good.zip"), "print('hello')");
        Files.writeString(subs.resolve("broken.zip"), "not a zip");

        Project project = buildProject(subs, "hello\n");
        ProjectRunner runner = new ProjectRunner(project, 30, 5);

        List<RunResult> results = runner.runAll();

        assertEquals(2, results.size());
        assertEquals(RunResult.Status.PASS, byId(results, "good").getStatus());
        assertEquals(RunResult.Status.COMPILE_ERROR, byId(results, "broken").getStatus());
    }

    @Test
    void missingSourceFileReportsCompileError(@TempDir Path tmp) throws IOException {
        assumeTrue(PYTHON != null, "no Python interpreter on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipNamed(subs.resolve("wrong-name.zip"), "wrong.py", "print('hi')");

        Project project = buildProject(subs, "hello\n");
        List<RunResult> results = new ProjectRunner(project, 30, 5).runAll();

        assertEquals(1, results.size());
        assertEquals(RunResult.Status.COMPILE_ERROR, results.get(0).getStatus());
        assertTrue(results.get(0).getErrorMessage().contains("main.py"));
    }

    @Test
    void infiniteLoopHitsRunTimeout(@TempDir Path tmp) throws IOException {
        assumeTrue(PYTHON != null, "no Python interpreter on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipScript(subs.resolve("loop.zip"), "while True:\n    pass");

        Project project = buildProject(subs, "hello\n");
        ProjectRunner runner = new ProjectRunner(project, 30, 1);

        List<RunResult> results = runner.runAll();

        assertEquals(RunResult.Status.TIMEOUT, results.get(0).getStatus());
    }

    @Test
    void listenerCallbacksFireInOrder(@TempDir Path tmp) throws IOException {
        assumeTrue(PYTHON != null, "no Python interpreter on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipScript(subs.resolve("a.zip"), "print('hello')");
        zipScript(subs.resolve("b.zip"), "print('hello')");

        Project project = buildProject(subs, "hello\n");
        ProjectRunner runner = new ProjectRunner(project, 30, 5);

        List<String> events = new ArrayList<>();
        runner.addListener(new ProjectRunner.RunListener() {
            @Override public void onSubmissionStarted(String studentId) { events.add("start:" + studentId); }
            @Override public void onSubmissionCompleted(RunResult r) { events.add("done:" + r.getStudentId()); }
            @Override public void onProgress(int completed, int total) { events.add("progress:" + completed + "/" + total); }
            @Override public void onAllCompleted() { events.add("all-done"); }
        });

        runner.runAll();

        assertEquals(List.of(
                "start:a", "done:a", "progress:1/2",
                "start:b", "done:b", "progress:2/2",
                "all-done"
        ), events);
    }

    @Test
    void resultsAreAlsoAccumulatedOnProject(@TempDir Path tmp) throws IOException {
        assumeTrue(PYTHON != null, "no Python interpreter on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipScript(subs.resolve("a.zip"), "print('hello')");

        Project project = buildProject(subs, "hello\n");
        new ProjectRunner(project, 30, 5).runAll();

        assertEquals(1, project.getResults().size());
    }

    @Test
    void rerunningClearsPreviousResults(@TempDir Path tmp) throws IOException {
        assumeTrue(PYTHON != null, "no Python interpreter on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipScript(subs.resolve("a.zip"), "print('hello')");

        Project project = buildProject(subs, "hello\n");
        ProjectRunner runner = new ProjectRunner(project, 30, 5);
        runner.runAll();
        runner.runAll();

        assertEquals(1, project.getResults().size());
    }

    @Test
    void emptySubmissionsDirReturnsNoResults(@TempDir Path tmp) throws IOException {
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        Project project = buildProject(subs, "hello\n");

        List<RunResult> results = new ProjectRunner(project, 30, 5).runAll();

        assertTrue(results.isEmpty());
    }

    @Test
    void missingActiveConfigurationFailsFast(@TempDir Path tmp) {
        Project broken = Project.newProject("p1", null, tmp.toString(), "", "hello\n");
        assertFalse(canConstruct(broken));
    }

    private static boolean canConstruct(Project p) {
        try {
            new ProjectRunner(p);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Project buildProject(Path subsDir, String expected) {
        Configuration cfg = new Configuration("cfg-1", "Python", LanguageType.PYTHON,
                false, null, null, PYTHON + " main.py", "main.py");
        return new Project("p-1", "Test", cfg, subsDir.toString(), "", expected);
    }

    private static RunResult byId(List<RunResult> all, String id) {
        return all.stream().filter(r -> r.getStudentId().equals(id)).findFirst().orElseThrow();
    }

    private static void zipScript(Path target, String body) throws IOException {
        zipNamed(target, "main.py", body);
    }

    private static void zipNamed(Path target, String name, String body) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(body.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    /** Returns the first working Python launcher on PATH, or {@code null}. */
    private static String detectPython() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] candidates = windows
                ? new String[]{"python", "py", "python3"}
                : new String[]{"python3", "python"};
        for (String candidate : candidates) {
            if (canRun(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean canRun(String launcher) {
        try {
            Process p = new ProcessBuilder(launcher, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}