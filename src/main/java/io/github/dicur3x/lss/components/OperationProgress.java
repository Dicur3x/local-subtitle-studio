package io.github.dicur3x.lss.components;

@FunctionalInterface
public interface OperationProgress {
    void update(String phase, long completedBytes, long totalBytes);
}
