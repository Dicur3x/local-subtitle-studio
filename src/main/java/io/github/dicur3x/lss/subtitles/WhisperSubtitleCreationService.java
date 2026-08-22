package io.github.dicur3x.lss.subtitles;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.audio.AudioExtractionException;
import io.github.dicur3x.lss.audio.FfmpegAudioExtractor;
import io.github.dicur3x.lss.audio.PreparedAudio;
import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.settings.ApplicationSettings;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WhisperSubtitleCreationService implements SubtitleCreationService {
    private static final Logger LOGGER = Logger.getLogger(WhisperSubtitleCreationService.class.getName());
    private final Supplier<ApplicationSettings> settingsSupplier;
    private final ExternalProcessRunner processRunner;
    private final ObjectMapper objectMapper;
    private final SubtitleTimingOptimizer timingOptimizer;
    private final SrtWriter srtWriter;

    public WhisperSubtitleCreationService(
            Supplier<ApplicationSettings> settingsSupplier,
            ExternalProcessRunner processRunner,
            ObjectMapper objectMapper
    ) {
        this(settingsSupplier, processRunner, objectMapper, new SubtitleTimingOptimizer(), new SrtWriter());
    }

    WhisperSubtitleCreationService(
            Supplier<ApplicationSettings> settingsSupplier,
            ExternalProcessRunner processRunner,
            ObjectMapper objectMapper,
            SubtitleTimingOptimizer timingOptimizer,
            SrtWriter srtWriter
    ) {
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.timingOptimizer = Objects.requireNonNull(timingOptimizer, "timingOptimizer");
        this.srtWriter = Objects.requireNonNull(srtWriter, "srtWriter");
    }

    @Override
    public CreatedSubtitles create(
            Path mediaFile,
            int audioStreamIndex,
            BooleanSupplier cancellationRequested,
            Consumer<String> progress
    ) throws SubtitleCreationException {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Objects.requireNonNull(progress, "progress");
        ApplicationSettings settings = Objects.requireNonNull(settingsSupplier.get(), "current settings");
        try {
            if (settings.whisperExecutable().isBlank()) {
                throw new SubtitleCreationException(
                        "whisper.cpp is not configured. Open Components and install it.");
            }
            Path whisperModel = requiredModel(settings.whisperModel(), "Whisper model");
            Path vadModel = requiredModel(settings.whisperVadModel(), "voice detection model");
            progress.accept("Preparing speech-recognition audio…");
            FfmpegAudioExtractor extractor = new FfmpegAudioExtractor(
                    settings.ffmpegExecutable(), settings.temporaryDirectory(), processRunner);
            PreparedAudio audio = extractor.extract(mediaFile, audioStreamIndex, cancellationRequested);
            try {
                throwIfCancelled(cancellationRequested);
                progress.accept("Recognizing speech locally with whisper.cpp…");
                WhisperCppTranscriber transcriber = new WhisperCppTranscriber(
                        settings.whisperExecutable(), whisperModel, vadModel, processRunner,
                        new WhisperJsonParser(objectMapper));
                TranscriptionResult result = transcriber.transcribe(audio.file(), cancellationRequested);
                throwIfCancelled(cancellationRequested);
                if (result.segments().isEmpty()) {
                    throw new SubtitleCreationException(
                            "No speech was recognized. Try another audio track or a higher-quality model.");
                }
                progress.accept("Optimizing subtitle timing…");
                List<SubtitleCue> cues = timingOptimizer.optimize(result.segments());
                Path output = srtWriter.write(mediaFile, result.language(), cues);
                return new CreatedSubtitles(output, result.language(), cues.size());
            } finally {
                try {
                    audio.close();
                } catch (IOException exception) {
                    LOGGER.log(Level.WARNING, "Could not remove temporary recognition audio", exception);
                }
            }
        } catch (CancellationException exception) {
            throw exception;
        } catch (AudioExtractionException exception) {
            throw new SubtitleCreationException(exception.getMessage(), exception);
        } catch (InvalidPathException exception) {
            throw new SubtitleCreationException(
                    "The configured Whisper model path is invalid. Open Components and reinstall the model.",
                    exception);
        }
    }

    private static Path requiredModel(String value, String description) throws SubtitleCreationException {
        if (value == null || value.isBlank()) {
            throw new SubtitleCreationException(
                    "A Whisper model is not configured. Open Components and choose a model.");
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new SubtitleCreationException(
                    "The configured " + description + " is missing. Open Components and reinstall the model.");
        }
        return path;
    }

    private static void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Subtitle creation was cancelled");
        }
    }
}
