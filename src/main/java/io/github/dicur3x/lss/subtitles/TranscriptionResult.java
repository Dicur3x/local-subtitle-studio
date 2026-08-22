package io.github.dicur3x.lss.subtitles;

import java.util.List;
import java.util.Objects;

public record TranscriptionResult(
        String language,
        List<RecognizedSegment> segments
) {
    public TranscriptionResult {
        language = Objects.requireNonNull(language, "language").strip().toLowerCase(java.util.Locale.ROOT);
        if (language.isEmpty()) {
            language = "original";
        }
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
    }
}
