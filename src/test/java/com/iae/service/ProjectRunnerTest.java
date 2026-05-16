package com.iae.service;

import com.iae.model.Configuration;
import com.iae.model.LanguageType;
import com.iae.model.Project;
import com.iae.model.RunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ProjectRunnerTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    @Test
    void passesAndFailsAreReportedPerSubmission(@TempDir Path tmp) throws IOException {
        assumeFalse(WINDOWS, "uses sh; covered manually on Windows");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));

        zipScript(subs.resolve("20240001.zip"), "echo hello");
        zipScript(subs.resolve("20240002.zip"), "echo wrong");
        zipScript(subs.resolve("20240003.zip"), "exit 1");

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
        assumeFalse(WINDOWS, "uses sh; covered manually on Windows");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));

        zipScript(subs.resolve("good.zip"), "echo hello");
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
        assumeFalse(WINDOWS, "uses sh; covered manually on Windows");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipNamed(subs.resolve("wrong-name.zip"), "wrong.sh", "echo hi");

        Project project = buildProject(subs, "hello\n");
        List<RunResult> results = new ProjectRunner(project, 30, 5).runAll();

        assertEquals(1, results.size());
        assertEquals(RunResult.Status.COMPILE_ERROR, results.get(0).getStatus());
        assertTrue(results.get(0).getErrorMessage().contains("hello.sh"));
    }

    @Test
    void infiniteLoopHitsRunTimeout(@TempDir Path tmp) throws IOException {
        assumeFalse(WINDOWS, "uses sh; covered manually on Windows");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipScript(subs.resolve("loop.zip"), "while true; do :; done");

        Project project = buildProject(subs, "hello\n");
        ProjectRunner runner = new ProjectRunner(project, 30, 1);

        List<RunResult> results = runner.runAll();

        assertEquals(RunResult.Status.TIMEOUT, results.get(0).getStatus());
    }

    @Test
    void listenerCallbacksFireInOrder(@TempDir Path tmp) throws IOException {
        assumeFalse(WINDOWS, "uses sh; covered manually on Windows");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipScript(subs.resolve("a.zip"), "echo hello");
        zipScript(subs.resolve("b.zip"), "echo hello");

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
        assumeFalse(WINDOWS, "uses sh; covered manually on Windows");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipScript(subs.resolve("a.zip"), "echo hello");

        Project project = buildProject(subs, "hello\n");
        new ProjectRunner(project, 30, 5).runAll();

        assertEquals(1, project.getResults().size());
    }

    @Test
    void rerunningClearsPreviousResults(@TempDir Path tmp) throws IOException {
        assumeFalse(WINDOWS, "uses sh; covered manually on Windows");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));
        zipScript(subs.resolve("a.zip"), "echo hello");

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
        Configuration cfg = new Configuration("cfg-1", "Shell", LanguageType.PYTHON,
                false, null, null, "sh hello.sh", "hello.sh");
        return new Project("p-1", "Test", cfg, subsDir.toString(), "", expected);
    }

    private static RunResult byId(List<RunResult> all, String id) {
        return all.stream().filter(r -> r.getStudentId().equals(id)).findFirst().orElseThrow();
    }

    private static void zipScript(Path target, String body) throws IOException {
        zipNamed(target, "hello.sh", body);
    }

    private static void zipNamed(Path target, String name, String body) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(body.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }
}
