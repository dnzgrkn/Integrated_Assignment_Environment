package com.iae.service.execution;

import com.iae.model.Configuration;
import com.iae.model.LanguageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class CommandRunnerTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private final CommandRunner runner = new CommandRunner();

    @Test
    void echoCapturesStdout(@TempDir Path tmp) {
        List<String> cmd = WINDOWS
                ? List.of("cmd", "/c", "echo", "hello")
                : List.of("sh", "-c", "echo hello");
        CommandRunner.CommandResult r = runner.runProcess(cmd, tmp, 5);

        assertEquals(0, r.exitCode);
        assertFalse(r.timedOut);
        assertTrue(r.stdout.contains("hello"), "stdout was: " + r.stdout);
    }

    @Test
    void nonZeroExitCodeIsCaptured(@TempDir Path tmp) {
        List<String> cmd = WINDOWS
                ? List.of("cmd", "/c", "exit 7")
                : List.of("sh", "-c", "exit 7");
        CommandRunner.CommandResult r = runner.runProcess(cmd, tmp, 5);

        assertEquals(7, r.exitCode);
        assertFalse(r.timedOut);
    }

    @Test
    void stderrIsCapturedSeparately(@TempDir Path tmp) {
        assumeFalse(WINDOWS, "cmd shell-quoting differs; skipping on Windows");
        List<String> cmd = List.of("sh", "-c", "echo OUT; echo ERR 1>&2");
        CommandRunner.CommandResult r = runner.runProcess(cmd, tmp, 5);

        assertEquals(0, r.exitCode);
        assertTrue(r.stdout.contains("OUT"));
        assertTrue(r.stderr.contains("ERR"));
        assertFalse(r.stdout.contains("ERR"));
    }

    @DisabledOnOs(value = OS.WINDOWS,
        disabledReason = "Windows process handle cleanup race with @TempDir after destroyForcibly")
    @Test
    void timeoutFlagsTimedOut(@TempDir Path tmp) {
        List<String> cmd = WINDOWS
                ? List.of("cmd", "/c", "ping", "-n", "10", "127.0.0.1")
                : List.of("sh", "-c", "sleep 10");
        long start = System.currentTimeMillis();
        CommandRunner.CommandResult r = runner.runProcess(cmd, tmp, 1);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(r.timedOut, "expected timeout flag");
        assertNotEquals(0, r.exitCode);
        assertTrue(elapsed < 8000, "should not have waited full 10s, got " + elapsed + "ms");
    }

    @Test
    void emptyCommandReturnsError(@TempDir Path tmp) {
        CommandRunner.CommandResult r = runner.runProcess(List.of(), tmp, 5);
        assertNotEquals(0, r.exitCode);
        assertFalse(r.timedOut);
    }

    @Test
    void compileSkippedWhenConfigNotCompiled(@TempDir Path tmp) {
        Configuration c = new Configuration("id-1", "Python", LanguageType.PYTHON,
                false, null, null, "python3 main.py", "main.py");

        CommandRunner.CommandResult r = runner.compile(c, tmp, 5);

        assertEquals(0, r.exitCode);
        assertFalse(r.timedOut);
        assertEquals("", r.stdout);
    }

    @Test
    void executeAppendsCommandLineArguments(@TempDir Path tmp) {
        assumeFalse(WINDOWS, "shell differs on Windows; covered manually");
        Configuration c = new Configuration("id-2", "Echo", LanguageType.PYTHON,
                false, null, null, "echo", "main");

        CommandRunner.CommandResult r = runner.execute(c, tmp, "first second", 5);

        assertEquals(0, r.exitCode);
        assertTrue(r.stdout.contains("first second"), "stdout was: " + r.stdout);
    }

    @Test
    void executeWithoutRunCommandReturnsError(@TempDir Path tmp) {
        Configuration c = new Configuration("id-3", "Broken", LanguageType.PYTHON,
                false, null, null, "", "main");

        CommandRunner.CommandResult r = runner.execute(c, tmp, null, 5);

        assertNotEquals(0, r.exitCode);
    }
}
