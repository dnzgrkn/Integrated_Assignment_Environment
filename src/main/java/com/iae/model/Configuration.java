package com.iae.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a reusable language-evaluation environment: the compiler or
 * interpreter to invoke, the flags to pass, the run command, and the expected
 * name of the source file inside each student submission.
 * <p>
 * Maps to Requirement R4 (create, edit, remove configurations) and Requirement
 * R5 (import / export configurations). See Section 5.1 of the design document.
 * <p>
 * Instances are plain data carriers — no I/O, no business logic. Persistence is
 * handled by {@link com.iae.persistence.ConfigurationRepository} via Jackson
 * JSON serialization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Configuration {

    private String id;
    private String name;
    private LanguageType languageType;
    private boolean compiled;
    private String compilerPath;
    private String compilerFlags;
    private String runCommand;
    private String expectedSourceFileName;

    /** No-arg constructor required by Jackson. */
    public Configuration() {
    }

    public Configuration(String id,
                         String name,
                         LanguageType languageType,
                         boolean compiled,
                         String compilerPath,
                         String compilerFlags,
                         String runCommand,
                         String expectedSourceFileName) {
        this.id = id;
        this.name = name;
        this.languageType = languageType;
        this.compiled = compiled;
        this.compilerPath = compilerPath;
        this.compilerFlags = compilerFlags;
        this.runCommand = runCommand;
        this.expectedSourceFileName = expectedSourceFileName;
    }

    /**
     * Factory that assigns a fresh UUID. Useful when the GUI creates a brand
     * new configuration through the Configuration Editor.
     */
    public static Configuration newConfiguration(String name,
                                                 LanguageType languageType,
                                                 boolean compiled,
                                                 String compilerPath,
                                                 String compilerFlags,
                                                 String runCommand,
                                                 String expectedSourceFileName) {
        return new Configuration(
                UUID.randomUUID().toString(),
                name,
                languageType,
                compiled,
                compilerPath,
                compilerFlags,
                runCommand,
                expectedSourceFileName);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LanguageType getLanguageType() {
        return languageType;
    }

    public void setLanguageType(LanguageType languageType) {
        this.languageType = languageType;
    }

    public boolean isCompiled() {
        return compiled;
    }

    public void setCompiled(boolean compiled) {
        this.compiled = compiled;
    }

    public String getCompilerPath() {
        return compilerPath;
    }

    public void setCompilerPath(String compilerPath) {
        this.compilerPath = compilerPath;
    }

    public String getCompilerFlags() {
        return compilerFlags;
    }

    public void setCompilerFlags(String compilerFlags) {
        this.compilerFlags = compilerFlags;
    }

    public String getRunCommand() {
        return runCommand;
    }

    public void setRunCommand(String runCommand) {
        this.runCommand = runCommand;
    }

    public String getExpectedSourceFileName() {
        return expectedSourceFileName;
    }

    public void setExpectedSourceFileName(String expectedSourceFileName) {
        this.expectedSourceFileName = expectedSourceFileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Configuration that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Configuration{id='" + id + "', name='" + name + "', languageType=" + languageType + "}";
    }
}