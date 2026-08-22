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
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WhisperCppTranscriber {
    private static final long MAXIMUM_JSON_BYTES = 128L * 1024 * 1024;
    private static final Pattern PROGRESS_LINE = Pattern.compile("progress\\s*=\\s*(\\d{1,3})%");

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
        return transcribe(audioFile, cancellationRequested, percent -> { });
    }

    public TranscriptionResult transcribe(
            Path audioFile,
            BooleanSupplier cancellationRequested,
            IntConsumer progress
    ) throws SubtitleCreationException {
        return transcribe(audioFile, SpokenLanguage.AUTO.code(), cancellationRequested, progress);
    }

    public TranscriptionResult transcribe(
            Path audioFile,
            String spokenLanguage,
            BooleanSupplier cancellationRequested,
            IntConsumer progress
    ) throws SubtitleCreationException {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Objects.requireNonNull(progress, "progress");
        String language;
        try {
            language = SpokenLanguage.requireSupportedCode(spokenLanguage);
        } catch (IllegalArgumentException exception) {
            throw new SubtitleCreationException(
                    "The selected spoken language is not supported by whisper.cpp.", exception);
        }
        Path audio = validateInputs(audioFile);
        Path outputBase = audio.getParent().resolve("whisper-" + UUID.randomUUID());
        Path outputJson = Path.of(outputBase + ".json");

        List<String> command = new ArrayList<>(List.of(
                executable,
                "--model", model.toString(),
                "--file", audio.toString(),
                "--language", language,
                "--output-json-full",
                "--output-file", outputBase.toString(),
                "--no-prints",
                "--print-progress",
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
            progress.accept(0);
            ProcessResult result = processRunner.runStreaming(
                    List.copyOf(command), cancellationRequested, line -> { },
                    line -> progressFromLine(line).ifPresent(progress));
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
            progress.accept(100);
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

    static java.util.OptionalInt progressFromLine(String line) {
        if (line == null) {
            return java.util.OptionalInt.empty();
        }
        Matcher matcher = PROGRESS_LINE.matcher(line);
        if (!matcher.find()) {
            return java.util.OptionalInt.empty();
        }
        int percent = Integer.parseInt(matcher.group(1));
        return java.util.OptionalInt.of(Math.max(0, Math.min(100, percent)));
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
