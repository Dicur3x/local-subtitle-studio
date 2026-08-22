package io.github.dicur3x.lss.infrastructure.process;

import java.util.Objects;

public record ProcessResult(int exitCode, String standardOutput, String standardError) {
    public ProcessResult {
        standardOutput = Objects.requireNonNull(standardOutput, "standardOutput");
        standardError = Objects.requireNonNull(standardError, "standardError");
    }
}
