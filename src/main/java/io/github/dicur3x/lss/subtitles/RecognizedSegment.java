package io.github.dicur3x.lss.subtitles;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record RecognizedSegment(
        long id,
        Duration speechStart,
        Duration speechEnd,
        String text,
        List<TokenTiming> tokens
) {
    public RecognizedSegment {
        if (id < 1) {
            throw new IllegalArgumentException("Segment id must be positive");
        }
        speechStart = requireNonNegative(speechStart, "speechStart");
        speechEnd = requireNonNegative(speechEnd, "speechEnd");
        if (speechEnd.compareTo(speechStart) <= 0) {
            throw new IllegalArgumentException("Speech end must follow speech start");
        }
        text = Objects.requireNonNull(text, "text").strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Segment text must not be blank");
        }
        tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }
}
