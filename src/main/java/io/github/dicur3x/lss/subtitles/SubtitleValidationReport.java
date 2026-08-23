package io.github.dicur3x.lss.subtitles;

import java.util.List;
import java.util.Objects;

public record SubtitleValidationReport(List<SubtitleWarning> warnings) {
    public SubtitleValidationReport {
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public boolean passedWithoutWarnings() {
        return warnings.isEmpty();
    }
}
