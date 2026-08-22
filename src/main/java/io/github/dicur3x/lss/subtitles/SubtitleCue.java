package io.github.dicur3x.lss.subtitles;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record SubtitleCue(
        long id,
        Duration start,
        Duration end,
        String originalText,
        List<TokenTiming> tokens
) {
    public SubtitleCue {
        if (id < 1) {
            throw new IllegalArgumentException("Cue id must be positive");
        }
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
        if (start.isNegative() || end.compareTo(start) <= 0) {
            throw new IllegalArgumentException("Cue timestamps are invalid");
        }
        originalText = Objects.requireNonNull(originalText, "originalText").strip();
        if (originalText.isEmpty()) {
            throw new IllegalArgumentException("Cue text must not be blank");
        }
        tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
    }
}
