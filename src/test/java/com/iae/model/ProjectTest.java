package com.iae.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTest {

    @Test
    void newProject_initializesEmptyResults() {
        Configuration cfg = Configuration.newConfiguration(
                "C", LanguageType.C, true, "gcc", "-o main", "./main", "main.c");
        Project p = Project.newProject("Asn1", cfg, "/tmp/subs", "1 2 3", "6\n");

        assertNotNull(p.getProjectId());
        assertEquals("Asn1", p.getProjectName());
        assertEquals(cfg, p.getActiveConfiguration());
        assertNotNull(p.getResults());
        assertTrue(p.getResults().isEmpty());
    }

    @Test
    void addResult_andClearResults() {
        Project p = Project.newProject("P", null, "/tmp", "", "");
        p.addResult(RunResult.pass("s1", "ok"));
        p.addResult(RunResult.fail("s2", "no", "mismatch"));
        assertEquals(2, p.getResults().size());

        p.clearResults();
        assertTrue(p.getResults().isEmpty());
    }

    @Test
    void setResults_replacesNullWithEmptyList() {
        Project p = new Project();
        p.setResults(null);
        assertNotNull(p.getResults());
        assertTrue(p.getResults().isEmpty());
    }
}