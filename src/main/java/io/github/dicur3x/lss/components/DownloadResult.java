package io.github.dicur3x.lss.components;

import java.util.Objects;

public record DownloadResult(long bytes, String sha256) {
    public DownloadResult {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes must not be negative");
        }
        sha256 = Objects.requireNonNull(sha256, "sha256").toLowerCase();
    }
}
