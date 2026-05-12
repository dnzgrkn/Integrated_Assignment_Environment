package com.iae.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * The verdict for one student submission after the full pipeline (extract →
 * compile/interpret → execute → compare) has run. See Section 5.1 and
 * Section 10.1 of the design document.
 * <p>
 * Every submission produces exactly one {@code RunResult}, regardless of
 * what went wrong — even unexpected exceptions become a RunResult with
 * {@link Status#RUNTIME_ERROR}. This is the central robustness mechanism
 * documented in Section 10.2.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RunResult {

    /**
     * Five-way classification of submission outcomes. See Section 10.1 of the
     * design document for the precise causes of each value.
     */
    public enum Status {
        /** Program compiled, ran, and produced the expected output. */
        PASS,
        /** Program ran but output did not match the expected output. */
        FAIL,
        /** ZIP extraction, structural validation, or compilation failed. */
        COMPILE_ERROR,
        /** Program started but exited non-zero without timing out. */
        RUNTIME_ERROR,
        /** Program did not terminate within the configured time limit. */
        TIMEOUT
    }

    private String studentId;
    private Status status;
    private String capturedOutput;
    private String errorMessage;

    /** No-arg constructor required by Jackson. */
    public RunResult() {
    }

    public RunResult(String studentId,
                     Status status,
                     String capturedOutput,
                     String errorMessage) {
        this.studentId = studentId;
        this.status = status;
        this.capturedOutput = capturedOutput;
        this.errorMessage = errorMessage;
    }

    /** Convenience factory for a PASS verdict. */
    public static RunResult pass(String studentId, String capturedOutput) {
        return new RunResult(studentId, Status.PASS, capturedOutput, null);
    }

    /** Convenience factory for a FAIL (output mismatch) verdict. */
    public static RunResult fail(String studentId, String capturedOutput, String mismatchDetails) {
        return new RunResult(studentId, Status.FAIL, capturedOutput, mismatchDetails);
    }

    /** Convenience factory for a COMPILE_ERROR verdict. */
    public static RunResult compileError(String studentId, String errorMessage) {
        return new RunResult(studentId, Status.COMPILE_ERROR, null, errorMessage);
    }

    /** Convenience factory for a RUNTIME_ERROR verdict. */
    public static RunResult runtimeError(String studentId, String capturedOutput, String errorMessage) {
        return new RunResult(studentId, Status.RUNTIME_ERROR, capturedOutput, errorMessage);
    }

    /** Convenience factory for a TIMEOUT verdict. */
    public static RunResult timeout(String studentId, String errorMessage) {
        return new RunResult(studentId, Status.TIMEOUT, null, errorMessage);
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getCapturedOutput() {
        return capturedOutput;
    }

    public void setCapturedOutput(String capturedOutput) {
        this.capturedOutput = capturedOutput;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RunResult that)) return false;
        return Objects.equals(studentId, that.studentId)
                && status == that.status
                && Objects.equals(capturedOutput, that.capturedOutput)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId, status, capturedOutput, errorMessage);
    }

    @Override
    public String toString() {
        return "RunResult{studentId='" + studentId + "', status=" + status + "}";
    }
}