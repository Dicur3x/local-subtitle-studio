package io.github.dicur3x.lss.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import io.github.dicur3x.lss.subtitles.CreatedSubtitles;
import io.github.dicur3x.lss.subtitles.SpokenLanguage;
import io.github.dicur3x.lss.subtitles.SrtWriter;
import io.github.dicur3x.lss.subtitles.SubtitleCreationException;
import io.github.dicur3x.lss.subtitles.SubtitleReadiness;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class LocalSubtitleTranslationWorkflow implements SubtitleTranslationWorkflow {
    private final Supplier<ApplicationSettings> settingsSupplier;
    private final Function<ApplicationSettings, TranslationEngine> engineFactory;

    public LocalSubtitleTranslationWorkflow(
            Supplier<ApplicationSettings> settingsSupplier,
            ExternalProcessRunner processRunner,
            ObjectMapper objectMapper
    ) {
        this(settingsSupplier, settings -> new LlamaCppTranslationEngine(
                settings.llamaExecutable(), Path.of(settings.translationModel()),
                processRunner, objectMapper));
    }

    LocalSubtitleTranslationWorkflow(
            Supplier<ApplicationSettings> settingsSupplier,
            Function<ApplicationSettings, TranslationEngine> engineFactory
    ) {
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
    }

    @Override
    public SubtitleReadiness readiness() {
        ApplicationSettings settings = Objects.requireNonNull(settingsSupplier.get(), "current settings");
        List<String> problems = new ArrayList<>();
        if (settings.llamaExecutable().isBlank()) {
            problems.add("llama.cpp is not configured");
        } else if (looksLikePath(settings.llamaExecutable())
                && !isReadableFile(settings.llamaExecutable())) {
            problems.add("llama.cpp was not found");
        }
        if (settings.translationModel().isBlank()) {
            problems.add("a translation model is not selected");
        } else if (!isReadableFile(settings.translationModel())) {
            problems.add("the translation model was not found");
        }
        return new SubtitleReadiness(problems.isEmpty(), problems);
    }

    @Override
    public CreatedTranslations translate(
            CreatedSubtitles source,
            String targetLanguage,
            BooleanSupplier cancellationRequested,
            IntConsumer progress
    ) throws TranslationException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Objects.requireNonNull(progress, "progress");
        if (source.cues().isEmpty()) {
            throw new TranslationException("The created SRT has no review data to translate.");
        }
        String target = SpokenLanguage.requireSupportedCode(targetLanguage);
        if (SpokenLanguage.AUTO.code().equals(target)) {
            throw new TranslationException("Choose a target language for translation.");
        }
        if (target.equalsIgnoreCase(source.language())) {
            throw new TranslationException("The source and target languages must be different.");
        }
        SubtitleReadiness readiness = readiness();
        if (!readiness.ready()) {
            throw new TranslationException("Open Components and install llama.cpp and a translation model.");
        }

        ApplicationSettings settings = settingsSupplier.get();
        try {
            TranslationEngine engine = engineFactory.apply(settings);
            TranslatedSubtitles translated = new SubtitleTranslationService(engine).translate(
                    source.cues(), languageName(source.language()), languageName(target),
                    cancellationRequested, progress);
            throwIfCancelled(cancellationRequested);
            SrtWriter writer = new SrtWriter(source.subtitlePreferences());
            Path translatedFile = writer.writeTranslated(
                    source.file(), source.language(), target, translated.translatedCues());
            return new CreatedTranslations(translatedFile, translated);
        } catch (CancellationException exception) {
            throw exception;
        } catch (SubtitleCreationException | InvalidPathException exception) {
            throw new TranslationException("Translation finished, but the translated SRT could not be saved.",
                    exception);
        }
    }

    private static String languageName(String code) {
        String normalized = code == null ? "" : code.strip().toLowerCase(Locale.ROOT);
        String name = Locale.forLanguageTag(normalized).getDisplayLanguage(Locale.ENGLISH);
        return name == null || name.isBlank() ? normalized : name;
    }

    private static boolean looksLikePath(String value) {
        try {
            return value.contains("/") || value.contains("\\") || Path.of(value).isAbsolute();
        } catch (InvalidPathException exception) {
            return true;
        }
    }

    private static boolean isReadableFile(String value) {
        try {
            return Files.isRegularFile(Path.of(value)) && Files.isReadable(Path.of(value));
        } catch (InvalidPathException exception) {
            return false;
        }
    }

    private static void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Subtitle translation was cancelled");
        }
    }
}
