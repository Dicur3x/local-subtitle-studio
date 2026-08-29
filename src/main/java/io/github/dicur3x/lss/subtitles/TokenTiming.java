package io.github.dicur3x.lss.subtitles;

import java.time.Duration;

public record TokenTiming(
        String text,
        Duration start,
        Duration end,
        double probability
) {
    public TokenTiming {
        text = text == null ? "" : text;
        start = requireNonNegative(start, "start");
        end = requireNonNegative(end, "end");
        if (end.compareTo(start) < 0) {
            throw new IllegalArgumentException("Token end must not precede its start");
        }
        if (!Double.isFinite(probability)) {
            throw new IllegalArgumentException("Token probability must be finite");
        }
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Duration duration = java.util.Objects.requireNonNull(value, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }
}
