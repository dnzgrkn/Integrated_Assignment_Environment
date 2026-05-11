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

class ProjectRepositoryTest {

    private Project sampleProject() {
        Configuration cfg = Configuration.newConfiguration(
                "C Standard", LanguageType.C, true,
                "gcc", "-Wall -o main", "./main", "main.c");
        Project p = Project.newProject("CE316 Demo", cfg, "/tmp/subs", "1 2", "3");
        p.addResult(RunResult.pass("s1", "3"));
        p.addResult(RunResult.compileError("s2", "syntax error"));
        return p;
    }

    @Test
    void saveAndLoadFromDirectory(@TempDir Path tmp) throws IOException {
        ProjectRepository repo = new ProjectRepository();
        Project p = sampleProject();
        repo.saveProject(p, tmp);

        Path projectFile = tmp.resolve(ProjectRepository.PROJECT_FILE_NAME);
        assertTrue(Files.exists(projectFile));

        Project loaded = repo.loadProject(tmp);
        assertEquals(p.getProjectId(), loaded.getProjectId());
        assertEquals(p.getProjectName(), loaded.getProjectName());
        assertEquals(2, loaded.getResults().size());
        assertEquals(RunResult.Status.PASS, loaded.getResults().get(0).getStatus());
        assertEquals(RunResult.Status.COMPILE_ERROR, loaded.getResults().get(1).getStatus());
    }

    @Test
    void loadProject_acceptsDirectFilePath(@TempDir Path tmp) throws IOException {
        ProjectRepository repo = new ProjectRepository();
        Project p = sampleProject();
        repo.saveProject(p, tmp);

        Path file = tmp.resolve(ProjectRepository.PROJECT_FILE_NAME);
        Project loaded = repo.loadProject(file);
        assertEquals(p.getProjectId(), loaded.getProjectId());
    }

    @Test
    void loadProject_throwsWhenMissing(@TempDir Path tmp) {
        ProjectRepository repo = new ProjectRepository();
        assertThrows(IOException.class, () -> repo.loadProject(tmp));
    }

    @Test
    void save_createsDirectoryIfMissing(@TempDir Path tmp) throws IOException {
        Path nested = tmp.resolve("a/b/c");
        ProjectRepository repo = new ProjectRepository();
        repo.saveProject(sampleProject(), nested);

        assertTrue(Files.exists(nested.resolve(ProjectRepository.PROJECT_FILE_NAME)));
    }
}