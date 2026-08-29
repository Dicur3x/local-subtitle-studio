package io.github.dicur3x.lss.translation;

import java.util.List;
import java.util.Objects;

public record TranslationBatchResult(List<TranslatedText> translations) {
    public TranslationBatchResult {
        translations = List.copyOf(Objects.requireNonNull(translations, "translations"));
    }
}
