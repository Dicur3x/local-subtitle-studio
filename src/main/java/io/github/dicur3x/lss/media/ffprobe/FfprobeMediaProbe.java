package io.github.dicur3x.lss.media.ffprobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import io.github.dicur3x.lss.media.MediaProbe;
import io.github.dicur3x.lss.media.MediaProbeException;
import io.github.dicur3x.lss.media.model.MediaInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FfprobeMediaProbe implements MediaProbe {
    private static final Logger LOGGER = Logger.getLogger(FfprobeMediaProbe.class.getName());
    private static final String SHOW_ENTRIES = String.join(":",
            "format=duration",
            "stream=index,codec_name,bit_rate,sample_rate,channels,channel_layout",
            "stream_tags=language,title"
    );

    private final String executable;
    private final ExternalProcessRunner processRunner;
    private final FfprobeJsonParser jsonParser;

    public FfprobeMediaProbe(String executable, ExternalProcessRunner processRunner) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.jsonParser = new FfprobeJsonParser(new ObjectMapper());
    }

    @Override
    public MediaInfo probe(Path mediaFile, BooleanSupplier cancellationRequested) throws MediaProbeException {
        Path normalizedFile = validate(mediaFile);
        List<String> command = List.of(
                executable,
                "-v", "error",
                "-select_streams", "a",
                "-show_entries", SHOW_ENTRIES,
                "-of", "json",
                normalizedFile.toString()
        );

        LOGGER.log(Level.INFO, "Inspecting media file {0}", normalizedFile);
        try {
            ProcessResult result = processRunner.run(command, cancellationRequested);
            if (result.exitCode() != 0) {
                String details = userSafeProcessError(result.standardError());
                LOGGER.log(Level.WARNING, "ffprobe failed with exit code {0}: {1}",
                        new Object[]{result.exitCode(), result.standardError().strip()});
                throw new MediaProbeException("ffprobe could not inspect this file" + details);
            }
            return jsonParser.parse(normalizedFile, result.standardOutput());
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Media inspection was interrupted");
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Could not start or read ffprobe", exception);
            if (isExecutableMissing(exception)) {
                throw new MediaProbeException(
                        "ffprobe was not found. Install FFmpeg or set LSS_FFPROBE_PATH.", exception);
            }
            throw new MediaProbeException("Could not read ffprobe output.", exception);
        }
    }

    private static Path validate(Path mediaFile) throws MediaProbeException {
        if (mediaFile == null) {
            throw new MediaProbeException("Choose a video file first.");
        }
        Path normalized = mediaFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new MediaProbeException("The selected file does not exist or cannot be read.");
        }
        return normalized;
    }

    private static boolean isExecutableMissing(IOException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("CreateProcess error=2")
                || message.contains("No such file or directory"));
    }

    private static String userSafeProcessError(String standardError) {
        String firstLine = standardError.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("");
        return firstLine.isEmpty() ? "." : ": " + firstLine;
    }
}
