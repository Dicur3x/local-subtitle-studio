package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class WhisperCppTranscriber {
    private static final long MAXIMUM_JSON_BYTES = 128L * 1024 * 1024;

    private final String executable;
    private final Path model;
    private final Path vadModel;
    private final ExternalProcessRunner processRunner;
    private final WhisperJsonParser parser;

    public WhisperCppTranscriber(
            String executable,
            Path model,
            Path vadModel,
            ExternalProcessRunner processRunner,
            WhisperJsonParser parser
    ) {
        this.executable = Objects.requireNonNull(executable, "executable").strip();
        this.model = Objects.requireNonNull(model, "model").toAbsolutePath().normalize();
        this.vadModel = Objects.requireNonNull(vadModel, "vadModel").toAbsolutePath().normalize();
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public TranscriptionResult transcribe(Path audioFile, BooleanSupplier cancellationRequested)
            throws SubtitleCreationException {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Path audio = validateInputs(audioFile);
        Path outputBase = audio.getParent().resolve("whisper-" + UUID.randomUUID());
        Path outputJson = Path.of(outputBase + ".json");

        List<String> command = new ArrayList<>(List.of(
                executable,
                "--model", model.toString(),
                "--file", audio.toString(),
                "--language", "auto",
                "--output-json-full",
                "--output-file", outputBase.toString(),
                "--no-prints",
                "--split-on-word",
                "--max-len", "84",
                "--word-thold", "0.01",
                "--suppress-nst",
                "--vad",
                "--vad-model", vadModel.toString(),
                "--vad-min-speech-duration-ms", "250",
                "--vad-min-silence-duration-ms", "200",
                "--vad-speech-pad-ms", "80",
                "--vad-samples-overlap", "0.1"
        ));

        try {
            throwIfCancelled(cancellationRequested);
            ProcessResult result = processRunner.run(List.copyOf(command), cancellationRequested);
            throwIfCancelled(cancellationRequested);
            if (result.exitCode() != 0) {
                throw new SubtitleCreationException("whisper.cpp could not recognize the selected audio"
                        + userSafeProcessError(result.standardError()));
            }
            if (!Files.isRegularFile(outputJson)) {
                throw new SubtitleCreationException("whisper.cpp did not create transcription data.");
            }
            if (Files.size(outputJson) > MAXIMUM_JSON_BYTES) {
                throw new SubtitleCreationException("whisper.cpp transcription data is unexpectedly large.");
            }
            return parser.parse(outputJson);
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Transcription was interrupted");
        } catch (IOException exception) {
            if (isExecutableMissing(exception)) {
                throw new SubtitleCreationException(
                        "whisper.cpp was not found. Open Components and install or update it.", exception);
            }
            throw new SubtitleCreationException("Could not run whisper.cpp.", exception);
        } finally {
            try {
                Files.deleteIfExists(outputJson);
            } catch (IOException ignored) {
                // The enclosing prepared-audio directory is removed after this operation.
            }
        }
    }

    private Path validateInputs(Path audioFile) throws SubtitleCreationException {
        if (executable.isEmpty()) {
            throw new SubtitleCreationException(
                    "whisper.cpp is not configured. Open Components and install it.");
        }
        Path audio = Objects.requireNonNull(audioFile, "audioFile").toAbsolutePath().normalize();
        if (!Files.isRegularFile(audio) || !Files.isReadable(audio)) {
            throw new SubtitleCreationException("The prepared audio file cannot be read.");
        }
        if (!Files.isRegularFile(model) || !Files.isReadable(model)) {
            throw new SubtitleCreationException(
                    "A Whisper model is not installed. Open Components and choose a model.");
        }
        if (!Files.isRegularFile(vadModel) || !Files.isReadable(vadModel)) {
            throw new SubtitleCreationException(
                    "The voice detection model is missing. Reinstall the selected model in Components.");
        }
        return audio;
    }

    private static void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Transcription was cancelled");
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
