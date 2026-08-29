package io.github.dicur3x.lss.subtitles;

import java.util.List;
import java.util.Objects;

public record SubtitleValidationReport(
        List<SubtitleWarning> warnings,
        List<SubtitleIssue> issues
) {
    public SubtitleValidationReport {
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public boolean passedWithoutWarnings() {
        return warnings.isEmpty();
    }
}
