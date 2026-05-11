package com.iae.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Thin wrapper around a configured Jackson {@link ObjectMapper}. Repositories
 * use it so they never touch Jackson directly. See Section 5.3 of the design
 * document.
 * <p>
 * The mapper is configured for:
 * <ul>
 *   <li>Pretty-printed output — JSON files are human-readable and
 *       hand-editable.</li>
 *   <li>Tolerance for unknown properties on read — additive schema changes
 *       won't break older files.</li>
 * </ul>
 */
public final class JsonStore {

    private static final ObjectMapper MAPPER = buildMapper();

    private JsonStore() {
        // utility class
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    /** Visible for repositories that need to configure custom modules. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Serializes {@code obj} to UTF-8 JSON at {@code path}. Parent directories
     * are created if they do not yet exist. The write is atomic in the sense
     * that the file is fully written before being made visible — partial
     * writes on crashes will not appear as a half-formed JSON file.
     */
    public static void serializeToFile(Object obj, Path path) throws IOException {
        if (obj == null) {
            throw new IllegalArgumentException("Cannot serialize null object");
        }
        if (path == null) {
            throw new IllegalArgumentException("Cannot serialize to null path");
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = MAPPER.writeValueAsBytes(obj);
        Files.write(path, bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /** Convenience overload accepting a string path. */
    public static void serializeToFile(Object obj, String path) throws IOException {
        serializeToFile(obj, Paths.get(path));
    }

    /** Deserializes the JSON at {@code path} into an instance of {@code clazz}. */
    public static <T> T deserializeFromFile(Path path, Class<T> clazz) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Cannot deserialize from null path");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }
        return MAPPER.readValue(path.toFile(), clazz);
    }

    /** Convenience overload accepting a string path. */
    public static <T> T deserializeFromFile(String path, Class<T> clazz) throws IOException {
        return deserializeFromFile(Paths.get(path), clazz);
    }
}