package io.github.dicur3x.lss.translation;

import io.github.dicur3x.lss.subtitles.SubtitleCue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Translates stable cue IDs in contextual batches without allowing timing changes. */
public final class SubtitleTranslationService {
    public static final int DEFAULT_BATCH_SIZE = 12;
    public static final int DEFAULT_CONTEXT_CUES = 2;

    private final TranslationEngine engine;
    private final int batchSize;
    private final int contextCues;

    public SubtitleTranslationService(TranslationEngine engine) {
        this(engine, DEFAULT_BATCH_SIZE, DEFAULT_CONTEXT_CUES);
    }

    SubtitleTranslationService(TranslationEngine engine, int batchSize, int contextCues) {
        this.engine = Objects.requireNonNull(engine, "engine");
        if (batchSize < 1 || contextCues < 0) {
            throw new IllegalArgumentException("Translation batch settings are invalid");
        }
        this.batchSize = batchSize;
        this.contextCues = contextCues;
    }

    public TranslatedSubtitles translate(
            List<SubtitleCue> cues,
            String sourceLanguage,
            String targetLanguage,
            BooleanSupplier cancellationRequested,
            IntConsumer progress
    ) throws TranslationException {
        List<SubtitleCue> safeCues = List.copyOf(Objects.requireNonNull(cues, "cues"));
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Objects.requireNonNull(progress, "progress");
        if (safeCues.isEmpty()) {
            throw new TranslationException("There are no subtitle cues to translate.");
        }

        int batchCount = (safeCues.size() + batchSize - 1) / batchSize;
        Map<Long, String> translatedById = new HashMap<>();
        progress.accept(0);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            throwIfCancelled(cancellationRequested);
            int targetFrom = batchIndex * batchSize;
            int targetTo = Math.min(safeCues.size(), targetFrom + batchSize);
            int contextFrom = Math.max(0, targetFrom - contextCues);
            int contextTo = Math.min(safeCues.size(), targetTo + contextCues);
            List<TranslationCueInput> inputs = new ArrayList<>(contextTo - contextFrom);
            for (int index = contextFrom; index < contextTo; index++) {
                SubtitleCue cue = safeCues.get(index);
                inputs.add(new TranslationCueInput(
                        cue.id(), cue.originalText(), index >= targetFrom && index < targetTo));
            }
            TranslationBatch batch = new TranslationBatch(sourceLanguage, targetLanguage, inputs);
            TranslationBatchResult result = engine.translate(batch, cancellationRequested);
            validateAndAppend(batch, result, translatedById);
            progress.accept((batchIndex + 1) * 100 / batchCount);
        }

        List<SubtitleCue> translatedCues = safeCues.stream().map(cue -> new SubtitleCue(
                cue.id(), cue.start(), cue.end(), translatedById.get(cue.id()), List.of())).toList();
        return new TranslatedSubtitles(
                sourceLanguage, targetLanguage, safeCues, translatedCues);
    }

    private static void validateAndAppend(
            TranslationBatch batch,
            TranslationBatchResult result,
            Map<Long, String> translatedById
    ) throws TranslationException {
        Set<Long> expected = new HashSet<>(batch.requestedIds());
        Set<Long> received = new HashSet<>();
        for (TranslatedText translation : Objects.requireNonNull(result, "result").translations()) {
            if (!expected.contains(translation.id())) {
                throw new TranslationException(
                        "The translation engine returned an unexpected cue ID: " + translation.id());
            }
            if (!received.add(translation.id()) || translatedById.containsKey(translation.id())) {
                throw new TranslationException(
                        "The translation engine returned a cue more than once: " + translation.id());
            }
            translatedById.put(translation.id(), translation.text());
        }
        if (!received.equals(expected)) {
            Set<Long> missing = new HashSet<>(expected);
            missing.removeAll(received);
            throw new TranslationException("The translation engine omitted cue IDs: " + missing);
        }
    }

    private static void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Subtitle translation was cancelled");
        }
    }
}
