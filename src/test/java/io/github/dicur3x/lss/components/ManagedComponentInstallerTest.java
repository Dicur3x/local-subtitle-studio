package io.github.dicur3x.lss.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedComponentInstallerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void verifiesExtractsAndActivatesFfmpegArchive() throws Exception {
        byte[] archive = ffmpegArchive();
        String checksum = sha256(archive);
        Path componentRoot = tempDirectory.resolve("components");
        var store = new ManagedComponentStore(componentRoot, new ObjectMapper());
        var installer = new ManagedComponentInstaller(new ArchiveClient(archive), store);
        var release = new ComponentRelease(
                ManagedComponent.FFMPEG,
                "9.0.1",
                URI.create("https://downloads.example.test/ffmpeg.zip"),
                Optional.of(checksum),
                URI.create("https://ffmpeg.org/download.html"),
                "Test release notes",
                URI.create("https://ffmpeg.org/releases/ffmpeg-9.0.1.tar.xz"),
                "GPLv3 test build"
        );

        InstalledComponent installed = installer.install(release, (phase, done, total) -> { }, () -> false);

        assertTrue(Files.isRegularFile(installed.primaryExecutablePath()));
        assertTrue(Files.isRegularFile(installed.secondaryExecutablePath()));
        assertEquals(checksum, installed.archiveSha256());
        assertEquals(installed, store.current(ManagedComponent.FFMPEG).orElseThrow());
        assertTrue(Files.isRegularFile(
                installed.primaryExecutablePath().getParent().getParent().resolve("LICENSE.txt")));
        try (var entries = Files.list(store.componentDirectory(ManagedComponent.FFMPEG))) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().startsWith(".install-")));
        }
    }

    @Test
    void installsLlamaCliAndAddsItsLicense() throws Exception {
        byte[] archive = llamaArchive();
        String checksum = sha256(archive);
        Path componentRoot = tempDirectory.resolve("components-llama");
        var store = new ManagedComponentStore(componentRoot, new ObjectMapper());
        var installer = new ManagedComponentInstaller(new ArchiveClient(archive), store);
        var release = new ComponentRelease(
                ManagedComponent.LLAMA_CPP,
                "b10621",
                URI.create("https://downloads.example.test/llama.zip"),
                Optional.of(checksum),
                URI.create("https://github.com/ggml-org/llama.cpp/releases/tag/v0.3.0"),
                "Test release notes",
                URI.create("https://github.com/ggml-org/llama.cpp/archive/refs/tags/v0.3.0.tar.gz"),
                "MIT License"
        );

        InstalledComponent installed = installer.install(
                release, (phase, done, total) -> { }, () -> false);

        assertEquals("llama-cli.exe", installed.primaryExecutablePath().getFileName().toString());
        assertTrue(Files.isRegularFile(installed.primaryExecutablePath().getParent().getParent()
                .resolve("LICENSE-llama.cpp.txt")));
        assertEquals(installed, store.current(ManagedComponent.LLAMA_CPP).orElseThrow());
    }

    private static byte[] ffmpegArchive() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "ffmpeg-9.0.1/bin/ffmpeg.exe", "ffmpeg");
            add(zip, "ffmpeg-9.0.1/bin/ffprobe.exe", "ffprobe");
            add(zip, "ffmpeg-9.0.1/LICENSE.txt", "GPLv3");
            add(zip, "ffmpeg-9.0.1/build.txt", "configuration");
        }
        return bytes.toByteArray();
    }

    private static byte[] llamaArchive() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "llama-b10621/llama-cli.exe", "llama");
            add(zip, "llama-b10621/ggml.dll", "library");
        }
        return bytes.toByteArray();
    }

    private static void add(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record ArchiveClient(byte[] archive) implements DownloadClient {
        @Override
        public String getText(URI uri, long maximumBytes, BooleanSupplier cancellationRequested) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DownloadResult download(
                URI uri, Path destination, long maximumBytes, String phase,
                OperationProgress progress, BooleanSupplier cancellationRequested
        ) throws IOException {
            Files.write(destination, archive);
            try {
                String checksum = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(archive));
                progress.update(phase, archive.length, archive.length);
                return new DownloadResult(archive.length, checksum);
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
