package io.github.dicur3x.lss.translation;

import io.github.dicur3x.lss.subtitles.CreatedSubtitles;
import io.github.dicur3x.lss.subtitles.SubtitleReadiness;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public interface SubtitleTranslationWorkflow {
    SubtitleReadiness readiness();

    CreatedTranslations translate(
            CreatedSubtitles source,
            String targetLanguage,
            BooleanSupplier cancellationRequested,
            IntConsumer progress
    ) throws TranslationException;
}
