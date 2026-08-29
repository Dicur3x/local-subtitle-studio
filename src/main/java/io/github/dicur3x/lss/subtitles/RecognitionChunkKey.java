package io.github.dicur3x.lss.subtitles;

import java.time.Duration;
import java.util.Objects;

/** Stable identity of a deterministic long-form recognition chunk. */
public record RecognitionChunkKey(
        int index,
        Duration offset,
        Duration keepFrom,
        Duration keepTo
) {
    public RecognitionChunkKey {
        if (index < 0) {
            throw new IllegalArgumentException("Chunk index must not be negative");
        }
        offset = requireNonNegative(offset, "offset");
        keepFrom = requireNonNegative(keepFrom, "keepFrom");
        keepTo = requireNonNegative(keepTo, "keepTo");
        if (keepTo.compareTo(keepFrom) <= 0) {
            throw new IllegalArgumentException("Chunk ownership end must follow its start");
        }
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }
}
