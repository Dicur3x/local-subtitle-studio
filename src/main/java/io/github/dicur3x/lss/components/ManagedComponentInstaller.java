package io.github.dicur3x.lss.components;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class ManagedComponentInstaller {
    private static final long MAXIMUM_ARCHIVE_BYTES = 768L * 1024 * 1024;
    private static final long MAXIMUM_EXTRACTED_BYTES = 4L * 1024 * 1024 * 1024;

    private final DownloadClient downloadClient;
    private final ManagedComponentStore store;

    public ManagedComponentInstaller(DownloadClient downloadClient, ManagedComponentStore store) {
        this.downloadClient = Objects.requireNonNull(downloadClient, "downloadClient");
        this.store = Objects.requireNonNull(store, "store");
    }

    public synchronized InstalledComponent install(
            ComponentRelease release,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws ComponentException {
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");

        Path componentDirectory = store.componentDirectory(release.component());
        Path stagingDirectory = null;
        try {
            Files.createDirectories(componentDirectory);
            stagingDirectory = Files.createTempDirectory(componentDirectory, ".install-");
            Path archive = stagingDirectory.resolve("download.zip");
            DownloadResult download = downloadClient.download(
                    release.downloadUri(), archive, MAXIMUM_ARCHIVE_BYTES,
                    "Downloading " + release.component().displayName(), progress, cancellationRequested);
            verifyExpectedChecksum(release, download.sha256());

            progress.update("Checking and unpacking " + release.component().displayName(),
                    download.bytes(), download.bytes());
            Path extracted = stagingDirectory.resolve("extracted");
            SecureZipExtractor.extract(archive, extracted, MAXIMUM_EXTRACTED_BYTES, cancellationRequested);
            checkCancellation(cancellationRequested);

            findExecutables(extracted, release.component());
            if (release.component() == ManagedComponent.WHISPER_CPP) {
                copyLicense("/third-party/whisper.cpp-LICENSE.txt",
                        extracted.resolve("LICENSE-whisper.cpp.txt"));
            } else if (release.component() == ManagedComponent.LLAMA_CPP) {
                copyLicense("/third-party/llama.cpp-LICENSE.txt",
                        extracted.resolve("LICENSE-llama.cpp.txt"));
            }

            String directoryName = safeDirectoryName(release.version()) + "-" + download.sha256().substring(0, 12);
            Path installationDirectory = componentDirectory.resolve(directoryName);
            if (Files.exists(installationDirectory) && !containsExecutables(
                    installationDirectory, release.component())) {
                installationDirectory = componentDirectory.resolve(
                        directoryName + "-" + java.util.UUID.randomUUID());
            }
            if (!Files.exists(installationDirectory)) {
                moveDirectory(extracted, installationDirectory);
            }

            Path primary = locateByName(installationDirectory, primaryExecutableName(release.component()));
            String secondary = release.component() == ManagedComponent.FFMPEG
                    ? locateByName(installationDirectory, "ffprobe.exe").toString() : "";

            InstalledComponent installed = new InstalledComponent(
                    InstalledComponent.CURRENT_SCHEMA_VERSION,
                    release.component().id(),
                    release.version(),
                    primary.toString(),
                    secondary,
                    download.sha256(),
                    release.downloadUri().toString(),
                    release.releaseNotesUri().toString(),
                    release.sourceCodeUri().toString(),
                    release.licenseSummary(),
                    Instant.now().toString()
            );
            store.activate(installed, installationDirectory);
            progress.update(release.component().displayName() + " is ready", download.bytes(), download.bytes());
            return installed;
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Component installation was interrupted");
        } catch (IOException exception) {
            throw new ComponentException("Could not install " + release.component().displayName() + ".", exception);
        } finally {
            if (stagingDirectory != null) {
                try {
                    ComponentFileOperations.deleteTree(stagingDirectory, componentDirectory);
                } catch (IOException ignored) {
                    // A partial staging directory is harmless and can be removed on the next housekeeping pass.
                }
            }
        }
    }

    private static void verifyExpectedChecksum(ComponentRelease release, String actual)
            throws ComponentException {
        if (release.expectedSha256().isPresent()
                && !release.expectedSha256().orElseThrow().equalsIgnoreCase(actual)) {
            throw new ComponentException(release.component().displayName()
                    + " failed its SHA-256 integrity check. Nothing was installed.");
        }
    }

    private static List<Path> findExecutables(Path root, ManagedComponent component) throws IOException {
        List<String> requiredNames = switch (component) {
            case FFMPEG -> List.of("ffmpeg.exe", "ffprobe.exe");
            case WHISPER_CPP -> List.of("whisper-cli.exe");
            case LLAMA_CPP -> List.of("llama-cli.exe");
        };
        List<Path> found;
        try (var files = Files.find(root, 8, (path, attributes) -> attributes.isRegularFile()
                && requiredNames.stream().anyMatch(name -> name.equalsIgnoreCase(path.getFileName().toString())))) {
            found = files.toList();
        }
        for (String required : requiredNames) {
            if (found.stream().noneMatch(path -> required.equalsIgnoreCase(path.getFileName().toString()))) {
                throw new IOException("Downloaded archive does not contain " + required);
            }
        }
        return found;
    }

    private static String primaryExecutableName(ManagedComponent component) {
        return switch (component) {
            case FFMPEG -> "ffmpeg.exe";
            case WHISPER_CPP -> "whisper-cli.exe";
            case LLAMA_CPP -> "llama-cli.exe";
        };
    }

    private static boolean containsExecutables(Path root, ManagedComponent component) {
        try {
            findExecutables(root, component);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static Path locateByName(Path root, String fileName) throws IOException {
        try (var files = Files.find(root, 8, (path, attributes) -> attributes.isRegularFile()
                && fileName.equalsIgnoreCase(path.getFileName().toString()))) {
            return files.min(Comparator.comparingInt(path -> path.getNameCount()))
                    .orElseThrow(() -> new IOException("Installed component does not contain " + fileName))
                    .toAbsolutePath().normalize();
        }
    }

    private static void copyLicense(String resourceName, Path destination) throws IOException {
        try (InputStream license = ManagedComponentInstaller.class.getResourceAsStream(resourceName)) {
            if (license == null) {
                throw new IOException("Bundled third-party license text is missing: " + resourceName);
            }
            Files.copy(license, destination);
        }
    }

    private static String safeDirectoryName(String version) {
        return version.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static void moveDirectory(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private static void checkCancellation(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Component installation was cancelled");
        }
    }
}
