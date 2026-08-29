package io.github.dicur3x.lss.infrastructure.tools;

import java.util.List;
import java.util.Objects;

public record ToolValidationReport(List<ToolCheck> checks) {
    public ToolValidationReport {
        checks = List.copyOf(Objects.requireNonNull(checks, "checks"));
    }

    public boolean requiredToolsAvailable() {
        return checks.stream().noneMatch(check -> check.requiredNow() && check.status() != ToolStatus.AVAILABLE);
    }
}
