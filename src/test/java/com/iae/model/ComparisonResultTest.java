package com.iae.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonResultTest {

    @Test
    void success_marksMatchTrue() {
        ComparisonResult r = ComparisonResult.success();
        assertTrue(r.isMatch());
    }

    @Test
    void mismatch_recordsLineNumberAndContents() {
        ComparisonResult r = ComparisonResult.mismatch(3, "foo", "bar");
        assertFalse(r.isMatch());
        assertEquals(3, r.getLineNumber());
        assertEquals("foo", r.getExpected());
        assertEquals("bar", r.getActual());
    }

    @Test
    void equalsAndHashCodeAreFieldBased() {
        ComparisonResult a = ComparisonResult.mismatch(2, "x", "y");
        ComparisonResult b = ComparisonResult.mismatch(2, "x", "y");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}