package com.iae.persistence;

import com.iae.model.Configuration;
import com.iae.model.LanguageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationRepositoryTest {

    private Configuration sample(String name, LanguageType lang) {
        return Configuration.newConfiguration(name, lang, true,
                "gcc", "-Wall", "./main", "main.c");
    }

    @Test
    void saveAndLoadAll(@TempDir Path tmp) throws IOException {
        ConfigurationRepository repo = new ConfigurationRepository(tmp);
        Configuration c1 = sample("C", LanguageType.C);
        Configuration c2 = sample("Java", LanguageType.JAVA);
        repo.save(c1);
        repo.save(c2);

        List<Configuration> all = repo.loadAll();
        assertEquals(2, all.size());
        assertTrue(all.contains(c1));
        assertTrue(all.contains(c2));
    }

    @Test
    void loadAll_returnsEmptyWhenDirMissing(@TempDir Path tmp) throws IOException {
        ConfigurationRepository repo = new ConfigurationRepository(tmp.resolve("nope"));
        assertTrue(repo.loadAll().isEmpty());
    }

    @Test
    void delete_removesFile(@TempDir Path tmp) throws IOException {
        ConfigurationRepository repo = new ConfigurationRepository(tmp);
        Configuration c = sample("C", LanguageType.C);
        repo.save(c);

        assertTrue(repo.delete(c.getId()));
        assertFalse(repo.delete(c.getId())); // already gone
        assertTrue(repo.loadAll().isEmpty());
    }

    @Test
    void importFromFile_persistsCopy(@TempDir Path tmp) throws IOException {
        // Create an "external" configuration JSON outside the repo.
        Path external = tmp.resolve("external.json");
        Configuration original = sample("Python", LanguageType.PYTHON);
        original.setCompiled(false);
        JsonStore.serializeToFile(original, external);

        Path repoDir = tmp.resolve("repo");
        ConfigurationRepository repo = new ConfigurationRepository(repoDir);
        Configuration imported = repo.importFromFile(external);

        assertEquals(original.getId(), imported.getId());
        assertEquals(LanguageType.PYTHON, imported.getLanguageType());
        assertFalse(imported.isCompiled());

        // It should also now be on disk in the managed directory.
        assertTrue(Files.exists(repoDir.resolve(original.getId() + ".json")));
        assertEquals(1, repo.loadAll().size());
    }

    @Test
    void exportToFile_writesStandaloneJson(@TempDir Path tmp) throws IOException {
        ConfigurationRepository repo = new ConfigurationRepository(tmp.resolve("repo"));
        Configuration c = sample("C", LanguageType.C);

        Path out = tmp.resolve("share/my-config.json");
        repo.exportToFile(c, out);

        assertTrue(Files.exists(out));
        Configuration loaded = JsonStore.deserializeFromFile(out, Configuration.class);
        assertEquals(c, loaded);
    }

    @Test
    void save_rejectsBlankId(@TempDir Path tmp) {
        ConfigurationRepository repo = new ConfigurationRepository(tmp);
        Configuration c = sample("C", LanguageType.C);
        c.setId("");
        assertThrows(IllegalArgumentException.class, () -> repo.save(c));
    }

    @Test
    void loadAll_skipsCorruptedFiles(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp);
        // One good file, one garbage file.
        Configuration good = sample("C", LanguageType.C);
        JsonStore.serializeToFile(good, tmp.resolve(good.getId() + ".json"));
        Files.writeString(tmp.resolve("broken.json"), "{ this is not valid json");

        ConfigurationRepository repo = new ConfigurationRepository(tmp);
        List<Configuration> loaded = repo.loadAll();
        // The broken file must not abort the whole load.
        assertEquals(1, loaded.size());
        assertEquals(good, loaded.get(0));
    }
}