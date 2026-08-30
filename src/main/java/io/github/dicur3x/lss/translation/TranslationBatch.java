package io.github.dicur3x.lss.translation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record TranslationBatch(
        String sourceLanguage,
        String targetLanguage,
        List<TranslationCueInput> cues
) {
    public TranslationBatch {
        sourceLanguage = languageCode(sourceLanguage, "sourceLanguage");
        targetLanguage = languageCode(targetLanguage, "targetLanguage");
        if (sourceLanguage.equals(targetLanguage)) {
            throw new IllegalArgumentException("Source and target languages must differ");
        }
        cues = List.copyOf(Objects.requireNonNull(cues, "cues"));
        if (cues.isEmpty() || cues.stream().noneMatch(TranslationCueInput::translate)) {
            throw new IllegalArgumentException("A translation batch must contain target cues");
        }
        long distinctIds = cues.stream().map(TranslationCueInput::id).distinct().count();
        if (distinctIds != cues.size()) {
            throw new IllegalArgumentException("Cue ids in a translation batch must be unique");
        }
    }

    public List<Long> requestedIds() {
        return cues.stream().filter(TranslationCueInput::translate)
                .map(TranslationCueInput::id).toList();
    }

    private static String languageCode(String value, String name) {
        String code = Objects.requireNonNull(value, name).strip().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z][a-z0-9_-]{1,31}")) {
            throw new IllegalArgumentException("Invalid language code: " + value);
        }
        return code;
    }
}
