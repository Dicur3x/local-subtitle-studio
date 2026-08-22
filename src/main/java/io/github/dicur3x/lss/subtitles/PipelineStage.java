package io.github.dicur3x.lss.subtitles;

public enum PipelineStage {
    PREPARING_AUDIO("Preparing audio", 0, 15),
    TRANSCRIBING("Recognizing speech", 15, 90),
    OPTIMIZING("Optimizing timing", 90, 96),
    VALIDATING("Checking subtitles", 96, 99),
    WRITING("Saving SRT", 99, 100),
    COMPLETE("Complete", 100, 100);

    private final String displayName;
    private final int overallStart;
    private final int overallEnd;

    PipelineStage(String displayName, int overallStart, int overallEnd) {
        this.displayName = displayName;
        this.overallStart = overallStart;
        this.overallEnd = overallEnd;
    }

    public String displayName() {
        return displayName;
    }

    int overallPercent(int stagePercent) {
        if (this == COMPLETE) {
            return 100;
        }
        int bounded = Math.max(0, Math.min(100, stagePercent));
        return overallStart + Math.round((overallEnd - overallStart) * bounded / 100f);
    }
}
