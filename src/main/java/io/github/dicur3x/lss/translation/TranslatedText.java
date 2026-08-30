package io.github.dicur3x.lss.translation;

import java.util.Objects;

public record TranslatedText(long id, String text) {
    public TranslatedText {
        if (id < 1) {
            throw new IllegalArgumentException("Cue id must be positive");
        }
        text = Objects.requireNonNull(text, "text").replaceAll("\\s+", " ").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Translated text must not be blank");
        }
    }
}
