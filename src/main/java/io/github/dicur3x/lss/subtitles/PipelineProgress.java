package io.github.dicur3x.lss.subtitles;

import java.util.Objects;

public record PipelineProgress(
        PipelineStage stage,
        int stagePercent,
        int overallPercent,
        String message
) {
    public PipelineProgress {
        stage = Objects.requireNonNull(stage, "stage");
        if (stagePercent < 0 || stagePercent > 100) {
            throw new IllegalArgumentException("Stage percentage must be between 0 and 100");
        }
        if (overallPercent < 0 || overallPercent > 100) {
            throw new IllegalArgumentException("Overall percentage must be between 0 and 100");
        }
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty()) {
            message = stage.displayName();
        }
    }

    public static PipelineProgress at(PipelineStage stage, int stagePercent, String message) {
        return new PipelineProgress(stage, stagePercent, stage.overallPercent(stagePercent), message);
    }

    public static PipelineProgress complete(String message) {
        return new PipelineProgress(PipelineStage.COMPLETE, 100, 100, message);
    }
}
