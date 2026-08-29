package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record CreatedSubtitles(
        Path file,
        String language,
        int cueCount,
        List<SubtitleWarning> warnings,
        List<SubtitleCue> cues,
        List<SubtitleIssue> issues,
        SubtitlePreferences subtitlePreferences
) {
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
        cues = List.copyOf(Objects.requireNonNull(cues, "cues"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        subtitlePreferences = Objects.requireNonNull(subtitlePreferences, "subtitlePreferences");
        if (!cues.isEmpty() && cues.size() != cueCount) {
            throw new IllegalArgumentException("Cue count does not match the review data");
        }
    }

    public CreatedSubtitles(Path file, String language, int cueCount) {
        this(file, language, cueCount, List.of(), List.of(), List.of(), SubtitlePreferences.defaults());
    }

    public CreatedSubtitles(Path file, String language, int cueCount, List<SubtitleWarning> warnings) {
        this(file, language, cueCount, warnings, List.of(), List.of(), SubtitlePreferences.defaults());
    }

    public CreatedSubtitles afterReview(
            List<SubtitleCue> reviewedCues,
            SubtitleValidationReport validation
    ) {
        Objects.requireNonNull(validation, "validation");
        List<SubtitleWarning> reviewedWarnings = new java.util.ArrayList<>(validation.warnings());
        warnings.stream()
                .filter(warning -> warning.type() == SubtitleWarningType.MIXED_VOICE_OVER)
                .forEach(reviewedWarnings::add);
        return new CreatedSubtitles(
                file, language, reviewedCues.size(), reviewedWarnings, reviewedCues,
                validation.issues(), subtitlePreferences);
    }
}
