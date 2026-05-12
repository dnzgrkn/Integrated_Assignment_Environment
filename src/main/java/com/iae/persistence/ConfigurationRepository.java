package com.iae.persistence;

import com.iae.model.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * File-based store for {@link Configuration} objects. One configuration per
 * JSON file, named {@code <id>.json}, kept under a single directory (by
 * default {@code %APPDATA%/IAE/configurations}; see Section 11.5 of the
 * design document).
 * <p>
 * Implements the Repository pattern (Section 4.4) so that the service layer
 * never has to talk to Jackson directly. Import / export operations satisfy
 * Requirement R5.
 */
public class ConfigurationRepository {

    private final Path directory;

    /**
     * Creates a repository rooted at {@code directory}. The directory is
     * created on first write if it does not yet exist.
     */
    public ConfigurationRepository(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Repository directory must not be null");
        }
        this.directory = directory;
    }

    /** Convenience overload accepting a string path. */
    public ConfigurationRepository(String directory) {
        this(Paths.get(directory));
    }

    /** Returns the default repository directory under the user's app data folder. */
    public static Path defaultDirectory() {
        String appData = System.getenv("APPDATA");
        Path base = (appData != null && !appData.isBlank())
                ? Paths.get(appData, "IAE")
                : Paths.get(System.getProperty("user.home"), ".iae");
        return base.resolve("configurations");
    }

    /** Loads every configuration JSON file in the directory. */
    public List<Configuration> loadAll() throws IOException {
        List<Configuration> result = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> jsonFiles = stream
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .toList();
            for (Path file : jsonFiles) {
                try {
                    result.add(JsonStore.deserializeFromFile(file, Configuration.class));
                } catch (IOException e) {
                    // Skip the bad file but keep going so one corrupted JSON
                    // doesn't take down the whole list.
                    System.err.println("Skipping unreadable configuration file: "
                            + file + " (" + e.getMessage() + ")");
                }
            }
        }
        return result;
    }

    /** Saves (or overwrites) the configuration. {@code config.id} must be set. */
    public void save(Configuration config) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("Configuration must not be null");
        }
        if (config.getId() == null || config.getId().isBlank()) {
            throw new IllegalArgumentException("Configuration id must not be null/blank");
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(config.getId() + ".json");
        JsonStore.serializeToFile(config, target);
    }

    /**
     * Deletes the configuration with the given id. Returns {@code true} if a
     * file was actually removed, {@code false} otherwise.
     */
    public boolean delete(String id) throws IOException {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Configuration id must not be null/blank");
        }
        Path target = directory.resolve(id + ".json");
        return Files.deleteIfExists(target);
    }

    /**
     * Imports a configuration from an arbitrary JSON file (Requirement R5).
     * The file is read into a {@link Configuration} object, persisted to the
     * managed directory (so it survives restarts), and returned.
     */
    public Configuration importFromFile(Path source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Source path must not be null");
        }
        Configuration imported = JsonStore.deserializeFromFile(source, Configuration.class);
        if (imported.getId() == null || imported.getId().isBlank()) {
            throw new IOException("Imported configuration has no id: " + source);
        }
        save(imported);
        return imported;
    }

    /** Convenience overload accepting a string path. */
    public Configuration importFromFile(String source) throws IOException {
        return importFromFile(Paths.get(source));
    }

    /**
     * Exports a configuration to an arbitrary destination path so the lecturer
     * can share it with a colleague (Requirement R5).
     */
    public void exportToFile(Configuration config, Path destination) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("Configuration must not be null");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destination path must not be null");
        }
        JsonStore.serializeToFile(config, destination);
    }

    /** Convenience overload accepting a string path. */
    public void exportToFile(Configuration config, String destination) throws IOException {
        exportToFile(config, Paths.get(destination));
    }

    /** Visible for tests. */
    public Path getDirectory() {
        return directory;
    }
}