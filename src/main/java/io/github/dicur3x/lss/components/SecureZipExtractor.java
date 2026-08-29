package io.github.dicur3x.lss.components;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class SecureZipExtractor {
    private static final int MAXIMUM_ENTRIES = 20_000;
    private static final int BUFFER_SIZE = 64 * 1024;

    private SecureZipExtractor() {
    }

    static void extract(
            Path archive,
            Path destination,
            long maximumExtractedBytes,
            BooleanSupplier cancellationRequested
    ) throws IOException {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        Files.createDirectories(normalizedDestination);
        long extractedBytes = 0;
        int entries = 0;

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                checkCancellation(cancellationRequested);
                if (++entries > MAXIMUM_ENTRIES) {
                    throw new IOException("ZIP archive contains too many files");
                }
                Path output = normalizedDestination.resolve(entry.getName()).normalize();
                if (!output.startsWith(normalizedDestination)) {
                    throw new IOException("ZIP archive contains an unsafe path");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                Files.createDirectories(output.getParent());
                try (var file = Files.newOutputStream(output,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        checkCancellation(cancellationRequested);
                        extractedBytes += read;
                        if (extractedBytes > maximumExtractedBytes) {
                            throw new IOException("ZIP archive expands beyond the allowed limit");
                        }
                        file.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private static void checkCancellation(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Component installation was cancelled");
        }
    }
}
