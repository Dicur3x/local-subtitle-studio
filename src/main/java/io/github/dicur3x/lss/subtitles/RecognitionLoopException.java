package io.github.dicur3x.lss.subtitles;

import java.time.Duration;
import java.util.Objects;

/** A long-form recognition attempt remained stuck after a clean-context retry. */
public final class RecognitionLoopException extends SubtitleCreationException {
    private final Duration position;

    public RecognitionLoopException(Duration position) {
        super("Recognition remained stuck near " + Objects.requireNonNull(position, "position"));
        this.position = position;
    }

    public Duration position() {
        return position;
    }
}
