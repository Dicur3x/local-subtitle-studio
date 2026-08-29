package io.github.dicur3x.lss.subtitles;

import java.util.Objects;

public record SubtitleWarning(SubtitleWarningType type, int count) {
    public SubtitleWarning {
        type = Objects.requireNonNull(type, "type");
        if (count < 1) {
            throw new IllegalArgumentException("Warning count must be positive");
        }
    }
}
