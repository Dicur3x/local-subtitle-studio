package io.github.dicur3x.lss.translation;

import java.util.Objects;

/** One cue passed to a translation engine; context-only cues must not be returned. */
public record TranslationCueInput(long id, String text, boolean translate) {
    public TranslationCueInput {
        if (id < 1) {
            throw new IllegalArgumentException("Cue id must be positive");
        }
        text = Objects.requireNonNull(text, "text").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Cue text must not be blank");
        }
    }
}
