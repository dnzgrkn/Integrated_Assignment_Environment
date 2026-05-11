package com.iae.model;
 
/**
 * Identifies the programming language a {@link Configuration} targets.
 * <p>
 * The IAE supports built-in reference templates for C, Java, and Python (see
 * Section 1.2 of the design document) but lecturers may also define
 * configurations for any other language whose compiler or interpreter is
 * reachable from the command line. {@link #OTHER} is the catch-all for those
 * cases.
 */
public enum LanguageType {
    C,
    JAVA,
    PYTHON,
    OTHER
}
 