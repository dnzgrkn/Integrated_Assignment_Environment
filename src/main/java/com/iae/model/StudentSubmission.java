package com.iae.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * One student's submission: the ID (derived from the ZIP filename), the path
 * to the original archive, and the path to the directory it was extracted to.
 * <p>
 * Construction notes:
 * <ul>
 *   <li>{@code studentId} and {@code zipFilePath} are known up front, before
 *       any processing happens.</li>
 *   <li>{@code extractedDirectoryPath} is set by {@code ZipExtractor} once
 *       Stage 2 (extraction) of the pipeline succeeds; it is {@code null}
 *       until then.</li>
 * </ul>
 * See Section 5.1 of the design document.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentSubmission {

    private String studentId;
    private String zipFilePath;
    private String extractedDirectoryPath;

    /** No-arg constructor required by Jackson. */
    public StudentSubmission() {
    }

    public StudentSubmission(String studentId, String zipFilePath) {
        this.studentId = studentId;
        this.zipFilePath = zipFilePath;
    }

    public StudentSubmission(String studentId,
                             String zipFilePath,
                             String extractedDirectoryPath) {
        this.studentId = studentId;
        this.zipFilePath = zipFilePath;
        this.extractedDirectoryPath = extractedDirectoryPath;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getZipFilePath() {
        return zipFilePath;
    }

    public void setZipFilePath(String zipFilePath) {
        this.zipFilePath = zipFilePath;
    }

    public String getExtractedDirectoryPath() {
        return extractedDirectoryPath;
    }

    public void setExtractedDirectoryPath(String extractedDirectoryPath) {
        this.extractedDirectoryPath = extractedDirectoryPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StudentSubmission that)) return false;
        return Objects.equals(studentId, that.studentId)
                && Objects.equals(zipFilePath, that.zipFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId, zipFilePath);
    }

    @Override
    public String toString() {
        return "StudentSubmission{studentId='" + studentId + "', zip='" + zipFilePath + "'}";
    }
}