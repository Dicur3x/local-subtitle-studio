package io.github.dicur3x.lss.subtitles;

import java.util.Objects;

public record SubtitleIssue(SubtitleWarningType type, long cueId) {
    public SubtitleIssue {
        type = Objects.requireNonNull(type, "type");
        if (cueId < 1) {
            throw new IllegalArgumentException("cueId must be positive");
        }
        if (type == SubtitleWarningType.MIXED_VOICE_OVER) {
            throw new IllegalArgumentException("Mixed voice-over is a result-wide warning");
        }
    }
}
