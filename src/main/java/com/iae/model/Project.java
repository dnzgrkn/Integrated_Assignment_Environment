package com.iae.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Top-level aggregate for a single grading session. Bundles:
 * <ul>
 *   <li>which {@link Configuration} to apply (compiler / interpreter setup),</li>
 *   <li>where the student ZIPs live ({@link #submissionsDirectoryPath}),</li>
 *   <li>what command-line arguments to pass to each student program,</li>
 *   <li>what output is expected, and</li>
 *   <li>the accumulated per-student {@link RunResult}s.</li>
 * </ul>
 * Maps to Requirement R3 (project creation / use) and Requirement R10 (open /
 * save projects). Persistence is handled by
 * {@link com.iae.persistence.ProjectRepository} (Section 6.2 of the design
 * document).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Project {

    private String projectId;
    private String projectName;
    private Configuration activeConfiguration;
    private String submissionsDirectoryPath;
    private String commandLineArguments;
    private String expectedOutput;
    private List<RunResult> results = new ArrayList<>();

    /** No-arg constructor required by Jackson. */
    public Project() {
    }

    public Project(String projectId,
                   String projectName,
                   Configuration activeConfiguration,
                   String submissionsDirectoryPath,
                   String commandLineArguments,
                   String expectedOutput) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.activeConfiguration = activeConfiguration;
        this.submissionsDirectoryPath = submissionsDirectoryPath;
        this.commandLineArguments = commandLineArguments;
        this.expectedOutput = expectedOutput;
    }

    /**
     * Factory that assigns a fresh UUID. Used by the GUI when the lecturer
     * creates a new project through the Project Editor dialog.
     */
    public static Project newProject(String projectName,
                                     Configuration activeConfiguration,
                                     String submissionsDirectoryPath,
                                     String commandLineArguments,
                                     String expectedOutput) {
        return new Project(
                UUID.randomUUID().toString(),
                projectName,
                activeConfiguration,
                submissionsDirectoryPath,
                commandLineArguments,
                expectedOutput);
    }

    /**
     * Replaces the result list with a fresh batch. Called by {@code
     * ProjectRunner} at the start of a new run so that stale results from a
     * previous run do not leak into the current one.
     */
    public void clearResults() {
        results.clear();
    }

    /** Appends one result. Convenience for the per-student loop. */
    public void addResult(RunResult result) {
        if (results == null) {
            results = new ArrayList<>();
        }
        results.add(result);
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Configuration getActiveConfiguration() {
        return activeConfiguration;
    }

    public void setActiveConfiguration(Configuration activeConfiguration) {
        this.activeConfiguration = activeConfiguration;
    }

    public String getSubmissionsDirectoryPath() {
        return submissionsDirectoryPath;
    }

    public void setSubmissionsDirectoryPath(String submissionsDirectoryPath) {
        this.submissionsDirectoryPath = submissionsDirectoryPath;
    }

    public String getCommandLineArguments() {
        return commandLineArguments;
    }

    public void setCommandLineArguments(String commandLineArguments) {
        this.commandLineArguments = commandLineArguments;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public List<RunResult> getResults() {
        return results;
    }

    public void setResults(List<RunResult> results) {
        this.results = (results != null) ? results : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Project that)) return false;
        return Objects.equals(projectId, that.projectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId);
    }

    @Override
    public String toString() {
        return "Project{id='" + projectId + "', name='" + projectName + "', results=" + results.size() + "}";
    }
}