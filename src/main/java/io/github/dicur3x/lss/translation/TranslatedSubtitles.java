package io.github.dicur3x.lss.translation;

import io.github.dicur3x.lss.subtitles.SubtitleCue;

import java.util.List;
import java.util.Objects;

public record TranslatedSubtitles(
        String sourceLanguage,
        String targetLanguage,
        List<SubtitleCue> originalCues,
        List<SubtitleCue> translatedCues
) {
    public TranslatedSubtitles {
        sourceLanguage = Objects.requireNonNull(sourceLanguage, "sourceLanguage");
        targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        originalCues = List.copyOf(Objects.requireNonNull(originalCues, "originalCues"));
        translatedCues = List.copyOf(Objects.requireNonNull(translatedCues, "translatedCues"));
        if (originalCues.isEmpty() || originalCues.size() != translatedCues.size()) {
            throw new IllegalArgumentException("Original and translated cue counts must match");
        }
        for (int index = 0; index < originalCues.size(); index++) {
            SubtitleCue original = originalCues.get(index);
            SubtitleCue translated = translatedCues.get(index);
            if (original.id() != translated.id()
                    || !original.start().equals(translated.start())
                    || !original.end().equals(translated.end())) {
                throw new IllegalArgumentException("Translation changed cue identity or timestamps");
            }
        }
    }

    public List<SubtitleCue> bilingualCues() {
        return java.util.stream.IntStream.range(0, originalCues.size())
                .mapToObj(index -> {
                    SubtitleCue original = originalCues.get(index);
                    SubtitleCue translated = translatedCues.get(index);
                    return new SubtitleCue(
                            original.id(), original.start(), original.end(),
                            original.originalText() + "\n" + translated.originalText(), List.of());
                }).toList();
    }
}
