package com.iae.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunResultTest {

    @Test
    void pass_setsStatusAndOutput() {
        RunResult r = RunResult.pass("123", "hello");
        assertEquals(RunResult.Status.PASS, r.getStatus());
        assertEquals("hello", r.getCapturedOutput());
        assertNull(r.getErrorMessage());
    }

    @Test
    void fail_setsStatusOutputAndMessage() {
        RunResult r = RunResult.fail("123", "actual", "expected: 1, got: 2");
        assertEquals(RunResult.Status.FAIL, r.getStatus());
        assertEquals("actual", r.getCapturedOutput());
        assertEquals("expected: 1, got: 2", r.getErrorMessage());
    }

    @Test
    void compileError_setsOnlyMessage() {
        RunResult r = RunResult.compileError("123", "syntax error at line 4");
        assertEquals(RunResult.Status.COMPILE_ERROR, r.getStatus());
        assertNull(r.getCapturedOutput());
        assertEquals("syntax error at line 4", r.getErrorMessage());
    }

    @Test
    void timeout_setsStatusAndMessage() {
        RunResult r = RunResult.timeout("123", "exceeded 10s");
        assertEquals(RunResult.Status.TIMEOUT, r.getStatus());
        assertEquals("exceeded 10s", r.getErrorMessage());
    }

    @Test
    void runtimeError_keepsPartialOutput() {
        RunResult r = RunResult.runtimeError("123", "first line\n", "NPE at line 7");
        assertEquals(RunResult.Status.RUNTIME_ERROR, r.getStatus());
        assertEquals("first line\n", r.getCapturedOutput());
        assertEquals("NPE at line 7", r.getErrorMessage());
    }
}