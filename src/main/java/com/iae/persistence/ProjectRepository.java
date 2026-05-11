package com.iae.persistence;

import com.iae.model.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Persists {@link Project} instances. Each project lives in its own directory
 * and is serialized as a single {@code project_data.json} file at the root of
 * that directory (Section 6.2 of the design document). Satisfies Requirement
 * R10 (open / save projects).
 * <p>
 * The submissions folder is treated as a sibling concern — the path is stored
 * in the project as {@link Project#getSubmissionsDirectoryPath()} but its
 * physical contents are not managed by this repository.
 */
public class ProjectRepository {

    /** Conventional filename for the serialized project. */
    public static final String PROJECT_FILE_NAME = "project_data.json";

    /**
     * Writes the project to {@code <projectDir>/project_data.json}. Parent
     * directories are created if needed.
     */
    public void saveProject(Project project, Path projectDir) throws IOException {
        if (project == null) {
            throw new IllegalArgumentException("Project must not be null");
        }
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory must not be null");
        }
        Files.createDirectories(projectDir);
        Path target = projectDir.resolve(PROJECT_FILE_NAME);
        JsonStore.serializeToFile(project, target);
    }

    /** Convenience overload accepting a string directory. */
    public void saveProject(Project project, String projectDir) throws IOException {
        saveProject(project, Paths.get(projectDir));
    }

    /**
     * Loads the project. {@code path} may be either:
     * <ul>
     *   <li>the directory containing {@code project_data.json}, or</li>
     *   <li>the {@code project_data.json} file itself.</li>
     * </ul>
     */
    public Project loadProject(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        Path file = Files.isDirectory(path) ? path.resolve(PROJECT_FILE_NAME) : path;
        if (!Files.exists(file)) {
            throw new IOException("Project file not found: " + file);
        }
        return JsonStore.deserializeFromFile(file, Project.class);
    }

    /** Convenience overload accepting a string path. */
    public Project loadProject(String path) throws IOException {
        return loadProject(Paths.get(path));
    }
}