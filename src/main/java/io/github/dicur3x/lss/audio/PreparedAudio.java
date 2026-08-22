package io.github.dicur3x.lss.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PreparedAudio implements AutoCloseable {
    public static final int SAMPLE_RATE = 16_000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;

    private final Path file;
    private final Path workingDirectory;
    private final AtomicBoolean closed = new AtomicBoolean();

    PreparedAudio(Path file, Path workingDirectory) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
        if (!this.file.getParent().equals(this.workingDirectory)) {
            throw new IllegalArgumentException("Prepared audio must be inside its working directory");
        }
    }

    public Path file() {
        return file;
    }

    public long size() throws IOException {
        return Files.size(file);
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(workingDirectory);
            } catch (IOException exception) {
                closed.set(false);
                throw exception;
            }
        }
    }
}
