package com.iae.persistence;

import com.iae.model.Configuration;
import com.iae.model.LanguageType;
import com.iae.model.Project;
import com.iae.model.RunResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JsonStoreTest {

    @Test
    void configurationRoundtrip(@TempDir Path tmp) throws IOException {
        Configuration original = Configuration.newConfiguration(
                "C Standard", LanguageType.C, true,
                "gcc", "-Wall -o main", "./main", "main.c");

        Path file = tmp.resolve("c-config.json");
        JsonStore.serializeToFile(original, file);

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);

        Configuration loaded = JsonStore.deserializeFromFile(file, Configuration.class);
        assertEquals(original, loaded);
        assertEquals(original.getName(), loaded.getName());
        assertEquals(original.getLanguageType(), loaded.getLanguageType());
        assertTrue(loaded.isCompiled());
    }

    @Test
    void projectWithResultsRoundtrip(@TempDir Path tmp) throws IOException {
        Configuration cfg = Configuration.newConfiguration(
                "Java", LanguageType.JAVA, true,
                "javac", "", "java Main", "Main.java");
        Project p = Project.newProject("Asn1", cfg, "/subs", "1 2", "3");
        p.addResult(RunResult.pass("s1", "3"));
        p.addResult(RunResult.fail("s2", "4", "expected 3 got 4"));
        p.addResult(RunResult.timeout("s3", "exceeded 10s"));

        Path file = tmp.resolve("project_data.json");
        JsonStore.serializeToFile(p, file);

        Project loaded = JsonStore.deserializeFromFile(file, Project.class);
        assertEquals(p.getProjectId(), loaded.getProjectId());
        assertEquals(3, loaded.getResults().size());
        assertEquals(RunResult.Status.PASS, loaded.getResults().get(0).getStatus());
        assertEquals(RunResult.Status.FAIL, loaded.getResults().get(1).getStatus());
        assertEquals(RunResult.Status.TIMEOUT, loaded.getResults().get(2).getStatus());
        assertEquals(cfg.getId(), loaded.getActiveConfiguration().getId());
    }

    @Test
    void unknownPropertiesAreTolerated(@TempDir Path tmp) throws IOException {
        // Simulate a schema-additive future version: extra field that current
        // code does not know about should not cause deserialization to fail.
        Path file = tmp.resolve("future.json");
        Files.writeString(file, """
                {
                  "id": "x",
                  "name": "Future",
                  "languageType": "C",
                  "compiled": true,
                  "futureFlag": "ignored"
                }
                """);
        Configuration loaded = JsonStore.deserializeFromFile(file, Configuration.class);
        assertEquals("x", loaded.getId());
        assertEquals(LanguageType.C, loaded.getLanguageType());
    }

    @Test
    void serializeToFile_rejectsNullObject(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
                () -> JsonStore.serializeToFile(null, tmp.resolve("x.json")));
    }

    @Test
    void deserializeFromFile_throwsWhenMissing(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.json");
        assertThrows(IOException.class,
                () -> JsonStore.deserializeFromFile(missing, Configuration.class));
    }
}