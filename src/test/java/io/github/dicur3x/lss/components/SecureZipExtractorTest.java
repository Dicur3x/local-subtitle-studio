package io.github.dicur3x.lss.components;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureZipExtractorTest {
    @TempDir
    Path tempDirectory;

    @Test
    void extractsFilesInsideDestination() throws Exception {
        Path archive = zipWith("folder/tool.exe", "binary");
        Path destination = tempDirectory.resolve("output");

        SecureZipExtractor.extract(archive, destination, 1024, () -> false);

        assertEquals("binary", Files.readString(destination.resolve("folder/tool.exe")));
    }

    @Test
    void rejectsZipSlipPaths() throws Exception {
        Path archive = zipWith("../escape.txt", "unsafe");
        Path destination = tempDirectory.resolve("output");

        assertThrows(IOException.class,
                () -> SecureZipExtractor.extract(archive, destination, 1024, () -> false));
        assertFalse(Files.exists(tempDirectory.resolve("escape.txt")));
    }

    private Path zipWith(String name, String value) throws IOException {
        Path archive = tempDirectory.resolve(name.hashCode() + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }
}
