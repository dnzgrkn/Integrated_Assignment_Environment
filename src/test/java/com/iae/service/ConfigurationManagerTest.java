package com.iae.service;

import com.iae.model.Configuration;
import com.iae.model.LanguageType;
import com.iae.persistence.ConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationManagerTest {

    @TempDir
    Path tmp;

    private ConfigurationManager manager;

    @BeforeEach
    void setUp() {
        ConfigurationManager.resetForTesting(new ConfigurationRepository(tmp));
        manager = ConfigurationManager.getInstance();
    }

    @Test
    void savedConfigurationAppearsInGetAll() throws IOException {
        Configuration c = newC("gcc-default");
        manager.save(c);

        List<Configuration> all = manager.getAll();
        assertEquals(1, all.size());
        assertEquals("gcc-default", all.get(0).getId());
    }

    @Test
    void getByIdReturnsSavedConfiguration() throws IOException {
        Configuration c = newC("py-default");
        manager.save(c);

        assertTrue(manager.getById("py-default").isPresent());
        assertFalse(manager.getById("ghost").isPresent());
    }

    @Test
    void deleteRemovesFromCacheAndDisk() throws IOException {
        Configuration c = newC("to-delete");
        manager.save(c);

        boolean removed = manager.delete("to-delete");

        assertTrue(removed);
        assertTrue(manager.getAll().isEmpty());
    }

    @Test
    void importConfigurationLoadsFromArbitraryPath() throws IOException {
        Path external = tmp.resolve("external.json");
        Files.writeString(external,
                "{\"id\":\"imported\",\"name\":\"Imported\",\"languageType\":\"C\","
                        + "\"compiled\":true,\"compilerPath\":\"gcc\",\"compilerFlags\":\"-O2\","
                        + "\"runCommand\":\"./a.out\",\"expectedSourceFileName\":\"main.c\"}");

        Configuration imported = manager.importConfiguration(external);

        assertEquals("imported", imported.getId());
        assertTrue(manager.getById("imported").isPresent());
    }

    @Test
    void exportConfigurationWritesFile() throws IOException {
        Configuration c = newC("export-me");
        Path dest = tmp.resolve("out.json");

        manager.exportConfiguration(c, dest);

        assertTrue(Files.isRegularFile(dest));
    }

    @Test
    void refreshReloadsFromDisk() throws IOException {
        Configuration c = newC("disk-only");
        new ConfigurationRepository(tmp).save(c);

        manager.refresh();

        assertTrue(manager.getById("disk-only").isPresent());
    }

    @Test
    void singletonReturnsSameInstanceUntilReset() {
        ConfigurationManager first = ConfigurationManager.getInstance();
        ConfigurationManager second = ConfigurationManager.getInstance();
        assertSame(first, second);

        ConfigurationManager.resetForTesting(new ConfigurationRepository(tmp));
        assertFalse(first == ConfigurationManager.getInstance());
    }

    private static Configuration newC(String id) {
        return new Configuration(id, "Test", LanguageType.C, true,
                "gcc", "-O2", "./main", "main.c");
    }
}
