package io.github.dicur3x.lss.subtitles;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

record RecognitionAudioChunk(
        Path file,
        Duration offset,
        Duration keepFrom,
        Duration keepTo,
        boolean temporary
) {
    RecognitionAudioChunk {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        offset = requireNonNegative(offset, "offset");
        keepFrom = requireNonNegative(keepFrom, "keepFrom");
        keepTo = requireNonNegative(keepTo, "keepTo");
        if (keepTo.compareTo(keepFrom) <= 0) {
            throw new IllegalArgumentException("keepTo must follow keepFrom");
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
