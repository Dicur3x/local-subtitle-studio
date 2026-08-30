package io.github.dicur3x.lss.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.components.ComponentException;
import io.github.dicur3x.lss.components.DownloadClient;
import io.github.dicur3x.lss.components.DownloadResult;
import io.github.dicur3x.lss.components.OperationProgress;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class TranslationModelManager {
    static final URI LICENSE_URI = URI.create(
            "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/LICENSE?download=true");
    static final String LICENSE_FILE_NAME = "LICENSE-Qwen3.txt";
    static final long LICENSE_SIZE = 11_544L;
    static final String LICENSE_SHA256 =
            "5de36594c10839788a8c589443a8ef9d8b8d17c65a1b5807206ae037fc36c6bd";
    private static final long DOWNLOAD_MARGIN = 1024L * 1024;
    private static final long FREE_SPACE_RESERVE = 256L * 1024 * 1024;

    private final Path modelsDirectory;
    private final DownloadClient downloadClient;
    private final ObjectMapper objectMapper;

    public TranslationModelManager(
            Path modelsDirectory,
            DownloadClient downloadClient,
            ObjectMapper objectMapper
    ) {
        this.modelsDirectory = Objects.requireNonNull(modelsDirectory, "modelsDirectory")
                .toAbsolutePath().normalize();
        this.downloadClient = Objects.requireNonNull(downloadClient, "downloadClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Optional<InstalledTranslationModel> current() throws ComponentException {
        Path metadata = modelsDirectory.resolve("current.json");
        if (!Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        try {
            InstalledTranslationModel installed = objectMapper.readValue(
                    metadata.toFile(), InstalledTranslationModel.class);
            TranslationModelProfile profile = installed.profile();
            if (!Files.isRegularFile(installed.modelPath())
                    || Files.size(installed.modelPath()) != profile.sizeBytes()
                    || !installed.modelSha256().equalsIgnoreCase(profile.sha256())) {
                return Optional.empty();
            }
            return Optional.of(installed);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ComponentException("Could not read the installed translation model metadata.", exception);
        }
    }

    public boolean isInstalled(TranslationModelProfile profile) {
        Objects.requireNonNull(profile, "profile");
        try {
            Path model = modelsDirectory.resolve(profile.fileName());
            return Files.isRegularFile(model) && Files.size(model) == profile.sizeBytes();
        } catch (IOException exception) {
            return false;
        }
    }

    public synchronized InstalledTranslationModel install(
            TranslationModelProfile profile,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws ComponentException {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        try {
            Files.createDirectories(modelsDirectory);
            Path model = modelsDirectory.resolve(profile.fileName());
            if (!isVerified(model, profile.sizeBytes(), profile.sha256(), cancellationRequested)) {
                requireDiskSpace(profile.sizeBytes() + FREE_SPACE_RESERVE);
                model = downloadVerified(profile.downloadUri(), profile.fileName(),
                        profile.sizeBytes(), profile.sha256(),
                        "Downloading " + profile.displayName(), progress, cancellationRequested);
            } else {
                progress.update("Downloading " + profile.displayName() + " (already verified)",
                        profile.sizeBytes(), profile.sizeBytes());
            }
            downloadVerified(LICENSE_URI, LICENSE_FILE_NAME, LICENSE_SIZE, LICENSE_SHA256,
                    "Downloading Qwen3 license", progress, cancellationRequested);

            InstalledTranslationModel installed = new InstalledTranslationModel(
                    InstalledTranslationModel.CURRENT_SCHEMA_VERSION,
                    profile.id(),
                    profile.modelName(),
                    model.toString(),
                    profile.sha256(),
                    profile.sourceUri().toString(),
                    "Apache License 2.0 · Copyright 2025 Alibaba Cloud",
                    Instant.now().toString()
            );
            writeJsonAtomically(modelsDirectory.resolve("current.json"), installed);
            progress.update("Translation model is ready", profile.sizeBytes(), profile.sizeBytes());
            return installed;
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Translation model installation was interrupted");
        } catch (IOException exception) {
            throw new ComponentException("Could not install the selected translation model.", exception);
        }
    }

    private Path downloadVerified(
            URI uri,
            String fileName,
            long expectedSize,
            String expectedSha256,
            String phase,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws IOException, InterruptedException, ComponentException {
        Path destination = modelsDirectory.resolve(fileName);
        if (isVerified(destination, expectedSize, expectedSha256, cancellationRequested)) {
            progress.update(phase + " (already verified)", expectedSize, expectedSize);
            return destination;
        }
        Path staging = Files.createTempDirectory(modelsDirectory, ".translation-model-");
        try {
            Path temporary = staging.resolve(fileName);
            DownloadResult result = downloadClient.download(
                    uri, temporary, expectedSize + DOWNLOAD_MARGIN,
                    phase, progress, cancellationRequested);
            if (result.bytes() != expectedSize || !expectedSha256.equalsIgnoreCase(result.sha256())) {
                throw new ComponentException("The downloaded " + fileName
                        + " failed its size or SHA-256 integrity check. Nothing was installed.");
            }
            moveReplacing(temporary, destination);
            return destination;
        } finally {
            deleteStaging(staging);
        }
    }

    private boolean isVerified(
            Path file,
            long expectedSize,
            String expectedSha256,
            BooleanSupplier cancellationRequested
    ) throws IOException {
        return Files.isRegularFile(file) && Files.size(file) == expectedSize
                && expectedSha256.equalsIgnoreCase(sha256(file, cancellationRequested));
    }

    private void requireDiskSpace(long requiredBytes) throws IOException, ComponentException {
        long usable = Files.getFileStore(modelsDirectory).getUsableSpace();
        if (usable < requiredBytes) {
            throw new ComponentException("Not enough free disk space for this translation model. "
                    + "Choose another application data folder or free additional space.");
        }
    }

    private static String sha256(Path file, BooleanSupplier cancellationRequested) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    checkCancellation(cancellationRequested);
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
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
            throw new IOException("Refusing to remove a path outside the translation model directory");
        }
        if (!Files.exists(normalized)) {
            return;
        }
        try (var paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void checkCancellation(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Translation model verification was cancelled");
        }
    }
}
