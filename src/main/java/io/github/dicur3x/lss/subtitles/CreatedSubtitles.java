package io.github.dicur3x.lss.subtitles;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record CreatedSubtitles(Path file, String language, int cueCount, List<String> warnings) {
    public CreatedSubtitles {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        language = Objects.requireNonNull(language, "language").strip();
        if (language.isEmpty()) {
            language = "original";
        }
        if (cueCount < 1) {
            throw new IllegalArgumentException("Cue count must be positive");
        }
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public CreatedSubtitles(Path file, String language, int cueCount) {
        this(file, language, cueCount, List.of());
    }
}
