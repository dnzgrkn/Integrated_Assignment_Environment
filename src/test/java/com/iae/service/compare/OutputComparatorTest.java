package com.iae.service.compare;

import com.iae.model.ComparisonResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputComparatorTest {

    private final OutputComparator comparator = new OutputComparator();

    @Test
    void identicalOutputsMatch() {
        ComparisonResult r = comparator.compare("hello\nworld\n", "hello\nworld\n");
        assertTrue(r.isMatch());
    }

    @Test
    void mismatchReportsOneBasedLineNumber() {
        ComparisonResult r = comparator.compare("a\nb\nXX\n", "a\nb\nc\n");
        assertFalse(r.isMatch());
        assertEquals(3, r.getLineNumber());
        assertEquals("c", r.getExpected());
        assertEquals("XX", r.getActual());
    }

    @Test
    void actualHasMoreLinesThanExpected() {
        ComparisonResult r = comparator.compare("a\nb\nextra\n", "a\nb\n");
        assertFalse(r.isMatch());
        assertEquals(3, r.getLineNumber());
        assertEquals("extra", r.getActual());
    }

    @Test
    void expectedHasMoreLinesThanActual() {
        ComparisonResult r = comparator.compare("a\n", "a\nmissing\n");
        assertFalse(r.isMatch());
        assertEquals(2, r.getLineNumber());
        assertEquals("missing", r.getExpected());
    }

    @Test
    void crlfVsLfStillMatches() {
        ComparisonResult r = comparator.compare("hello\r\nworld\r\n", "hello\nworld\n");
        assertTrue(r.isMatch());
    }

    @Test
    void trailingWhitespaceIgnored() {
        ComparisonResult r = comparator.compare("hello   \nworld\t\n", "hello\nworld\n");
        assertTrue(r.isMatch());
    }

    @Test
    void trailingEmptyLinesIgnored() {
        ComparisonResult r = comparator.compare("hello\nworld\n\n\n\n", "hello\nworld\n");
        assertTrue(r.isMatch());
    }

    @Test
    void bothEmptyMatch() {
        ComparisonResult r = comparator.compare("", "");
        assertTrue(r.isMatch());
    }

    @Test
    void nullActualReportsMismatch() {
        ComparisonResult r = comparator.compare(null, "hello\n");
        assertFalse(r.isMatch());
        assertEquals(1, r.getLineNumber());
        assertEquals("hello", r.getExpected());
    }
}
