package com.iae.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {

    @Test
    void newConfiguration_assignsUuid() {
        Configuration c = Configuration.newConfiguration(
                "C", LanguageType.C, true,
                "gcc", "-Wall -o main", "./main", "main.c");
        assertNotNull(c.getId());
        assertFalse(c.getId().isBlank());
    }

    @Test
    void equalsAndHashCode_useIdOnly() {
        Configuration a = new Configuration("id-1", "A", LanguageType.C, true,
                "gcc", "", "./main", "main.c");
        Configuration b = new Configuration("id-1", "B", LanguageType.JAVA, false,
                "javac", "x", "java", "Main.java");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void settersUpdateFields() {
        Configuration c = new Configuration();
        c.setName("Python 3");
        c.setLanguageType(LanguageType.PYTHON);
        c.setCompiled(false);
        c.setRunCommand("python3 main.py");

        assertEquals("Python 3", c.getName());
        assertEquals(LanguageType.PYTHON, c.getLanguageType());
        assertFalse(c.isCompiled());
        assertEquals("python3 main.py", c.getRunCommand());
    }
}