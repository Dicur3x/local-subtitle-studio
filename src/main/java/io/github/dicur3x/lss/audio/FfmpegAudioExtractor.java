package io.github.dicur3x.lss.audio;

import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FfmpegAudioExtractor implements AudioExtractor {
    private static final Logger LOGGER = Logger.getLogger(FfmpegAudioExtractor.class.getName());
    private static final long MINIMUM_WAV_BYTES = 44;

    private final String executable;
    private final String configuredTemporaryDirectory;
    private final ExternalProcessRunner processRunner;

    public FfmpegAudioExtractor(
            String executable,
            String configuredTemporaryDirectory,
            ExternalProcessRunner processRunner
    ) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.configuredTemporaryDirectory = Objects.requireNonNull(
                configuredTemporaryDirectory, "configuredTemporaryDirectory");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    @Override
    public PreparedAudio extract(Path mediaFile, int streamIndex, BooleanSupplier cancellationRequested)
            throws AudioExtractionException {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        if (cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Audio preparation was cancelled");
        }
        Path normalizedMedia = validateInput(mediaFile, streamIndex);
        Path workingDirectory = createWorkingDirectory();
        Path outputFile = workingDirectory.resolve("track-" + streamIndex + ".wav");

        List<String> command = List.of(
                executable,
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-i", normalizedMedia.toString(),
                "-map", "0:" + streamIndex,
                "-vn",
                "-sn",
                "-dn",
                "-ac", Integer.toString(PreparedAudio.CHANNELS),
                "-ar", Integer.toString(PreparedAudio.SAMPLE_RATE),
                "-c:a", "pcm_s16le",
                outputFile.toString()
        );

        LOGGER.log(Level.INFO, "Preparing stream {0} from {1}", new Object[]{streamIndex, normalizedMedia});
        try {
            ProcessResult result = processRunner.run(command, cancellationRequested);
            if (result.exitCode() != 0) {
                throw new AudioExtractionException("FFmpeg could not prepare the selected audio track"
                        + userSafeProcessError(result.standardError()));
            }
            if (!Files.isRegularFile(outputFile) || Files.size(outputFile) <= MINIMUM_WAV_BYTES) {
                throw new AudioExtractionException("FFmpeg did not create a valid WAV file.");
            }
            return new PreparedAudio(outputFile, workingDirectory);
        } catch (CancellationException exception) {
            cleanupPartial(outputFile, workingDirectory);
            throw exception;
        } catch (InterruptedException exception) {
            cleanupPartial(outputFile, workingDirectory);
            Thread.currentThread().interrupt();
            throw new CancellationException("Audio preparation was interrupted");
        } catch (IOException exception) {
            cleanupPartial(outputFile, workingDirectory);
            if (isExecutableMissing(exception)) {
                throw new AudioExtractionException(
                        "FFmpeg was not found. Open Settings and choose ffmpeg.exe.", exception);
            }
            throw new AudioExtractionException("Could not prepare the selected audio track.", exception);
        } catch (AudioExtractionException exception) {
            cleanupPartial(outputFile, workingDirectory);
            throw exception;
        }
    }

    private Path createWorkingDirectory() throws AudioExtractionException {
        try {
            if (configuredTemporaryDirectory.isBlank()) {
                return Files.createTempDirectory("local-subtitle-studio-");
            }
            Path root = Path.of(configuredTemporaryDirectory).toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (!Files.isWritable(root)) {
                throw new AudioExtractionException("Configured temporary directory is not writable.");
            }
            return Files.createTempDirectory(root, "local-subtitle-studio-");
        } catch (InvalidPathException exception) {
            throw new AudioExtractionException("Configured temporary directory path is invalid.", exception);
        } catch (IOException exception) {
            throw new AudioExtractionException("Could not create a temporary working directory.", exception);
        }
    }

    private static Path validateInput(Path mediaFile, int streamIndex) throws AudioExtractionException {
        if (mediaFile == null || !Files.isRegularFile(mediaFile) || !Files.isReadable(mediaFile)) {
            throw new AudioExtractionException("The selected media file cannot be read.");
        }
        if (streamIndex < 0) {
            throw new AudioExtractionException("The selected audio stream index is invalid.");
        }
        return mediaFile.toAbsolutePath().normalize();
    }

    private static void cleanupPartial(Path outputFile, Path workingDirectory) {
        try {
            Files.deleteIfExists(outputFile);
            Files.deleteIfExists(workingDirectory);
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Could not remove incomplete temporary audio", exception);
        }
    }

    private static boolean isExecutableMissing(IOException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("CreateProcess error=2")
                || message.contains("No such file or directory"));
    }

    private static String userSafeProcessError(String standardError) {
        String firstLine = standardError.lines().map(String::strip).filter(line -> !line.isEmpty())
                .findFirst().orElse("");
        return firstLine.isEmpty() ? "." : ": " + firstLine;
    }
}
