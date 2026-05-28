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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end integration tests that exercise {@link ProjectRunner#runAll()}
 * against <em>real external toolchains</em>, going all the way through the
 * pipeline: ZIP extraction -> compile/interpret -> execute -> compare -> report.
 * <p>
 * Unlike the unit-style coverage in {@link ProjectRunnerTest}, these tests drive
 * an actual compiler ({@code javac} + {@code java}) and an actual interpreter
 * ({@code python}/{@code python3}), so they also cover the {@code isCompiled=true}
 * branch (the compile stage) that an interpreter-only test never reaches.
 * <p>
 * Each test resolves its toolchain at runtime and is skipped (via assumptions)
 * rather than failed when the toolchain is unavailable, so the suite stays green
 * on a machine that happens to lack one of them.
 */
class ProjectRunnerIntegrationTest {

    private static boolean JAVAC_AVAILABLE;
    private static String PYTHON;

    @BeforeAll
    static void resolveToolchains() {
        JAVAC_AVAILABLE = canRun("javac", "-version") && canRun("java", "-version");
        PYTHON = detectPython();
    }

    /**
     * Full compiled-language run: three Java submissions producing a PASS, a FAIL
     * (output mismatch) and a COMPILE_ERROR (invalid source). Verifies the compile
     * stage, the execute stage and per-submission isolation against the real
     * {@code javac}/{@code java} toolchain.
     */
    @Test
    void compiledJavaSubmissionsRunEndToEnd(@TempDir Path tmp) throws IOException {
        assumeTrue(JAVAC_AVAILABLE, "JDK (javac/java) not on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));

        zip(subs.resolve("20240001.zip"), "Main.java",
                "public class Main{public static void main(String[] a){System.out.println(\"hello\");}}");
        zip(subs.resolve("20240002.zip"), "Main.java",
                "public class Main{public static void main(String[] a){System.out.println(\"oops\");}}");
        zip(subs.resolve("20240003.zip"), "Main.java",
                "public class Main{ not valid java }");

        Configuration cfg = new Configuration("java-cfg", "Java", LanguageType.JAVA,
                true, "javac", "", "java Main", "Main.java");
        Project project = new Project("p-java", "Java E2E", cfg, subs.toString(), "", "hello\n");

        List<RunResult> results = new ProjectRunner(project, 30, 10).runAll();

        assertEquals(3, results.size());
        assertEquals(RunResult.Status.PASS, byId(results, "20240001").getStatus());
        assertEquals(RunResult.Status.FAIL, byId(results, "20240002").getStatus());
        assertEquals(RunResult.Status.COMPILE_ERROR, byId(results, "20240003").getStatus());
        assertNotNull(byId(results, "20240003").getErrorMessage());
        assertTrue(byId(results, "20240003").getErrorMessage().toLowerCase().contains("main.java"));
        assertEquals(3, project.getResults().size());
    }

    /**
     * Full interpreted-language run: a single passing Python submission. Confirms
     * that the compile stage is correctly skipped for interpreted configurations
     * and that stdout is captured and compared against the expected output.
     */
    @Test
    void interpretedPythonSubmissionRunsEndToEnd(@TempDir Path tmp) throws IOException {
        assumeTrue(PYTHON != null, "no Python interpreter on PATH");
        Path subs = Files.createDirectories(tmp.resolve("submissions"));

        zip(subs.resolve("20240010.zip"), "main.py", "print('hello world')");

        Configuration cfg = new Configuration("py-cfg", "Python", LanguageType.PYTHON,
                false, null, null, PYTHON + " main.py", "main.py");
        Project project = new Project("p-py", "Python E2E", cfg, subs.toString(), "", "hello world\n");

        List<RunResult> results = new ProjectRunner(project, 30, 10).runAll();

        assertEquals(1, results.size());
        RunResult r = results.get(0);
        assertEquals(RunResult.Status.PASS, r.getStatus());
        assertTrue(r.getCapturedOutput().contains("hello world"));
    }

    // ---- helpers ----

    private static RunResult byId(List<RunResult> all, String id) {
        return all.stream().filter(r -> r.getStudentId().equals(id)).findFirst().orElseThrow();
    }

    private static void zip(Path target, String entryName, String body) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(body.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    private static String detectPython() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] candidates = windows
                ? new String[]{"python", "py", "python3"}
                : new String[]{"python3", "python"};
        for (String candidate : candidates) {
            if (canRun(candidate, "--version")) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean canRun(String exe, String versionFlag) {
        try {
            Process p = new ProcessBuilder(exe, versionFlag)
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