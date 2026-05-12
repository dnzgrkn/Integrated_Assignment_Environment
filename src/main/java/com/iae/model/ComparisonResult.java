package com.iae.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * Outcome of comparing a student program's captured output against the
 * project's expected output. Produced by
 * {@code com.iae.service.compare.OutputComparator} (see Section 9.4 of the
 * design document).
 * <p>
 * When {@link #isMatch()} is {@code true}, the other fields carry no
 * meaningful information. When it is {@code false}, {@link #getLineNumber()}
 * is the 1-based index of the first divergent line, and {@link #getExpected()}
 * / {@link #getActual()} are the contents at that line (one of them may be
 * the sentinel {@code "<no line>"} when one side has more lines than the
 * other).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComparisonResult {

    private boolean match;
    private int lineNumber;
    private String expected;
    private String actual;

    /** No-arg constructor required by Jackson. */
    public ComparisonResult() {
    }

    private ComparisonResult(boolean match,
                             int lineNumber,
                             String expected,
                             String actual) {
        this.match = match;
        this.lineNumber = lineNumber;
        this.expected = expected;
        this.actual = actual;
    }

    /** Successful comparison — outputs match. */
    public static ComparisonResult success() {
        return new ComparisonResult(true, -1, null, null);
    }

    /** Mismatch at the given 1-based line. */
    public static ComparisonResult mismatch(int lineNumber, String expected, String actual) {
        return new ComparisonResult(false, lineNumber, expected, actual);
    }

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getActual() {
        return actual;
    }

    public void setActual(String actual) {
        this.actual = actual;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComparisonResult that)) return false;
        return match == that.match
                && lineNumber == that.lineNumber
                && Objects.equals(expected, that.expected)
                && Objects.equals(actual, that.actual);
    }

    @Override
    public int hashCode() {
        return Objects.hash(match, lineNumber, expected, actual);
    }

    @Override
    public String toString() {
        if (match) {
            return "ComparisonResult{match=true}";
        }
        return "ComparisonResult{match=false, line=" + lineNumber
                + ", expected='" + expected + "', actual='" + actual + "'}";
    }
}