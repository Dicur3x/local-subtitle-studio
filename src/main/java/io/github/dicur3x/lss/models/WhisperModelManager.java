package io.github.dicur3x.lss.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.components.ComponentException;
import io.github.dicur3x.lss.components.DownloadClient;
import io.github.dicur3x.lss.components.DownloadResult;
import io.github.dicur3x.lss.components.OperationProgress;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class WhisperModelManager {
    static final String VAD_FILE_NAME = "ggml-silero-v6.2.0.bin";
    static final long VAD_SIZE = 885_098L;
    static final String VAD_SHA256 = "2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987";
    static final URI VAD_DOWNLOAD_URI = URI.create(
            "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v6.2.0.bin?download=true");

    private final Path modelsDirectory;
    private final DownloadClient downloadClient;
    private final ObjectMapper objectMapper;

    public WhisperModelManager(Path modelsDirectory, DownloadClient downloadClient, ObjectMapper objectMapper) {
        this.modelsDirectory = Objects.requireNonNull(modelsDirectory, "modelsDirectory")
                .toAbsolutePath().normalize();
        this.downloadClient = Objects.requireNonNull(downloadClient, "downloadClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Optional<InstalledModelBundle> current() throws ComponentException {
        Path metadata = modelsDirectory.resolve("current.json");
        if (!Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        try {
            InstalledModelBundle bundle = objectMapper.readValue(metadata.toFile(), InstalledModelBundle.class);
            if (!Files.isRegularFile(bundle.modelPath()) || !Files.isRegularFile(bundle.vadModelPath())) {
                return Optional.empty();
            }
            return Optional.of(bundle);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ComponentException("Could not read the installed Whisper model metadata.", exception);
        }
    }

    public synchronized InstalledModelBundle install(
            WhisperModelProfile profile,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws ComponentException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        try {
            Files.createDirectories(modelsDirectory);
            Path model = ensureVerifiedFile(
                    profile.downloadUri(), profile.fileName(), profile.sizeBytes(), profile.sha256(),
                    "Downloading " + profile.displayName(), progress, cancellationRequested);
            Path vad = ensureVerifiedFile(
                    VAD_DOWNLOAD_URI, VAD_FILE_NAME, VAD_SIZE, VAD_SHA256,
                    "Downloading Silero VAD", progress, cancellationRequested);
            copyLicenseIfMissing("/third-party/openai-whisper-LICENSE.txt",
                    modelsDirectory.resolve("LICENSE-OpenAI-Whisper.txt"));
            copyLicenseIfMissing("/third-party/silero-vad-LICENSE.txt",
                    modelsDirectory.resolve("LICENSE-Silero-VAD.txt"));

            InstalledModelBundle bundle = new InstalledModelBundle(
                    InstalledModelBundle.CURRENT_SCHEMA_VERSION,
                    profile.id(),
                    profile.modelName(),
                    model.toString(),
                    profile.sha256(),
                    vad.toString(),
                    VAD_SHA256,
                    Instant.now().toString()
            );
            writeJsonAtomically(modelsDirectory.resolve("current.json"), bundle);
            progress.update("Model and voice detection are ready",
                    profile.sizeBytes() + VAD_SIZE, profile.sizeBytes() + VAD_SIZE);
            return bundle;
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Model installation was interrupted");
        } catch (IOException exception) {
            throw new ComponentException("Could not install the selected Whisper model.", exception);
        }
    }

    private Path ensureVerifiedFile(
            URI uri,
            String fileName,
            long expectedSize,
            String expectedSha256,
            String phase,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws IOException, InterruptedException, ComponentException {
        Path destination = modelsDirectory.resolve(fileName);
        if (Files.isRegularFile(destination) && Files.size(destination) == expectedSize
                && expectedSha256.equalsIgnoreCase(sha256(destination, cancellationRequested))) {
            progress.update(phase + " (already verified)", expectedSize, expectedSize);
            return destination;
        }

        Path staging = Files.createTempDirectory(modelsDirectory, ".model-");
        try {
            Path temporary = staging.resolve(fileName);
            DownloadResult result = downloadClient.download(
                    uri, temporary, expectedSize + 1024 * 1024, phase, progress, cancellationRequested);
            if (result.bytes() != expectedSize || !expectedSha256.equalsIgnoreCase(result.sha256())) {
                throw new ComponentException("The downloaded " + fileName
                        + " failed its size or SHA-256 integrity check. Nothing was installed.");
            }
            moveReplacing(temporary, destination);
            return destination;
        } finally {
            try {
                deleteStaging(staging);
            } catch (IOException ignored) {
                // The incomplete staging folder is never activated and can be removed later.
            }
        }
    }

    private static String sha256(Path file, BooleanSupplier cancellationRequested) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
                        throw new CancellationException("Model verification was cancelled");
                    }
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void writeJsonAtomically(Path destination, Object value) throws IOException {
        Path temporary = Files.createTempFile(modelsDirectory, ".metadata-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            moveReplacing(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void copyLicenseIfMissing(String resource, Path destination) throws IOException {
        if (Files.exists(destination)) {
            return;
        }
        try (InputStream input = WhisperModelManager.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Bundled third-party license text is missing: " + resource);
            }
            Files.copy(input, destination);
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteStaging(Path staging) throws IOException {
        Path normalized = staging.toAbsolutePath().normalize();
        if (!normalized.startsWith(modelsDirectory) || normalized.equals(modelsDirectory)) {
            throw new IOException("Refusing to remove a path outside the model directory");
        }
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
