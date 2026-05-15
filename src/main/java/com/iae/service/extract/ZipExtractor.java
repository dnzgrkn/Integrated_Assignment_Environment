package com.iae.service.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipExtractor {

    public Path extract(Path zipFilePath, Path destinationDir) throws ExtractionException {
        if (zipFilePath == null || destinationDir == null) {
            throw new ExtractionException("zipFilePath and destinationDir must not be null");
        }
        if (!Files.isRegularFile(zipFilePath)) {
            throw new ExtractionException("ZIP file not found: " + zipFilePath);
        }

        try {
            Files.createDirectories(destinationDir);
        } catch (IOException e) {
            throw new ExtractionException("Cannot create destination: " + destinationDir, e);
        }

        Path destRoot = destinationDir.toAbsolutePath().normalize();

        int entryCount = 0;
        try (InputStream in = Files.newInputStream(zipFilePath);
             ZipInputStream zis = new ZipInputStream(in)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                Path target = destinationDir.resolve(entry.getName()).toAbsolutePath().normalize();

                if (!target.startsWith(destRoot)) {
                    throw new ExtractionException("Invalid ZIP entry path: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        } catch (ExtractionException e) {
            throw e;
        } catch (IOException e) {
            throw new ExtractionException("Failed to extract " + zipFilePath + ": " + e.getMessage(), e);
        }

        // ZipInputStream is lenient: a non-ZIP file reads as zero entries instead of erroring.
        if (entryCount == 0) {
            throw new ExtractionException("Not a valid ZIP file: " + zipFilePath);
        }

        return destinationDir;
    }

    public boolean validateStructure(Path extractedDir, String expectedFileName) {
        if (extractedDir == null || expectedFileName == null || !Files.isDirectory(extractedDir)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(extractedDir, 3)) {
            return walk.anyMatch(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().equals(expectedFileName));
        } catch (IOException e) {
            return false;
        }
    }
}
