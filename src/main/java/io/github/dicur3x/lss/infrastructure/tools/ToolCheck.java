package io.github.dicur3x.lss.infrastructure.tools;

import java.util.Objects;

public record ToolCheck(String name, ToolStatus status, boolean requiredNow, String details) {
    public ToolCheck {
        name = Objects.requireNonNull(name, "name");
        status = Objects.requireNonNull(status, "status");
        details = Objects.requireNonNull(details, "details");
    }
}
