package com.iae.service.extract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipExtractorTest {

    private final ZipExtractor extractor = new ZipExtractor();

    @Test
    void extractsValidZipAndReturnsDestination(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("submission.zip");
        writeZip(zip, entry("main.c", "int main(){return 0;}\n"));

        Path dest = tmp.resolve("out");
        Path result = extractor.extract(zip, dest);

        assertEquals(dest, result);
        assertTrue(Files.exists(dest.resolve("main.c")));
        assertEquals("int main(){return 0;}\n",
                Files.readString(dest.resolve("main.c"), StandardCharsets.UTF_8));
    }

    @Test
    void createsNestedDirectoriesFromZipEntries(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("nested.zip");
        writeZip(zip,
                entry("20240001/", null),
                entry("20240001/main.c", "// hi\n"));

        Path dest = tmp.resolve("out");
        extractor.extract(zip, dest);

        assertTrue(Files.isDirectory(dest.resolve("20240001")));
        assertTrue(Files.exists(dest.resolve("20240001/main.c")));
    }

    @Test
    void throwsOnCorruptZip(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("broken.zip");
        Files.writeString(zip, "this is not a zip file");

        assertThrows(ExtractionException.class,
                () -> extractor.extract(zip, tmp.resolve("out")));
    }

    @Test
    void throwsWhenZipFileMissing(@TempDir Path tmp) {
        assertThrows(ExtractionException.class,
                () -> extractor.extract(tmp.resolve("nope.zip"), tmp.resolve("out")));
    }

    @Test
    void blocksZipSlipAttempt(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("evil.zip");
        writeZip(zip, entry("../../../evil.txt", "pwned\n"));

        ExtractionException ex = assertThrows(ExtractionException.class,
                () -> extractor.extract(zip, tmp.resolve("out")));
        assertTrue(ex.getMessage().contains("Invalid ZIP entry path"));
    }

    @Test
    void validateStructureFindsFileAtTopLevel(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("main.c"), "int main(){}");
        assertTrue(extractor.validateStructure(tmp, "main.c"));
    }

    @Test
    void validateStructureFindsNestedFile(@TempDir Path tmp) throws Exception {
        Path nested = tmp.resolve("20240001");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("main.c"), "int main(){}");

        assertTrue(extractor.validateStructure(tmp, "main.c"));
    }

    @Test
    void validateStructureReturnsFalseWhenMissing(@TempDir Path tmp) {
        assertFalse(extractor.validateStructure(tmp, "main.c"));
    }

    @Test
    void validateStructureReturnsFalseForNonExistentDir(@TempDir Path tmp) {
        assertFalse(extractor.validateStructure(tmp.resolve("ghost"), "main.c"));
    }

    private record ZipPart(String name, String content) {}

    private static ZipPart entry(String name, String content) {
        return new ZipPart(name, content);
    }

    private static void writeZip(Path target, ZipPart... parts) throws IOException {
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            for (ZipPart part : parts) {
                ZipEntry e = new ZipEntry(part.name());
                zos.putNextEntry(e);
                if (part.content() != null) {
                    zos.write(part.content().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
    }
}
