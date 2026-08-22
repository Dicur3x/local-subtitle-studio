package io.github.dicur3x.lss.settings;

public record SubtitlePreferences(
        int maximumCharactersPerLine,
        int maximumLines,
        int minimumDurationMs,
        int startPaddingMs,
        int endPaddingMs,
        int nextSpeechGapMs,
        double maximumCharactersPerSecond
) {
    public static final int DEFAULT_MAXIMUM_CHARACTERS_PER_LINE = 42;
    public static final int DEFAULT_MAXIMUM_LINES = 2;
    public static final int DEFAULT_MINIMUM_DURATION_MS = 800;
    public static final int DEFAULT_START_PADDING_MS = 50;
    public static final int DEFAULT_END_PADDING_MS = 200;
    public static final int DEFAULT_NEXT_SPEECH_GAP_MS = 100;
    public static final double DEFAULT_MAXIMUM_CHARACTERS_PER_SECOND = 20d;

    public SubtitlePreferences {
        maximumCharactersPerLine = inRange(maximumCharactersPerLine, 10, 100)
                ? maximumCharactersPerLine : DEFAULT_MAXIMUM_CHARACTERS_PER_LINE;
        maximumLines = inRange(maximumLines, 1, 4) ? maximumLines : DEFAULT_MAXIMUM_LINES;
        minimumDurationMs = inRange(minimumDurationMs, 200, 10_000)
                ? minimumDurationMs : DEFAULT_MINIMUM_DURATION_MS;
        startPaddingMs = inRange(startPaddingMs, 0, 5_000)
                ? startPaddingMs : DEFAULT_START_PADDING_MS;
        endPaddingMs = inRange(endPaddingMs, 0, 5_000)
                ? endPaddingMs : DEFAULT_END_PADDING_MS;
        nextSpeechGapMs = inRange(nextSpeechGapMs, 0, 5_000)
                ? nextSpeechGapMs : DEFAULT_NEXT_SPEECH_GAP_MS;
        maximumCharactersPerSecond = Double.isFinite(maximumCharactersPerSecond)
                && maximumCharactersPerSecond >= 5d && maximumCharactersPerSecond <= 100d
                ? maximumCharactersPerSecond : DEFAULT_MAXIMUM_CHARACTERS_PER_SECOND;
    }

    public static SubtitlePreferences defaults() {
        return new SubtitlePreferences(
                DEFAULT_MAXIMUM_CHARACTERS_PER_LINE,
                DEFAULT_MAXIMUM_LINES,
                DEFAULT_MINIMUM_DURATION_MS,
                DEFAULT_START_PADDING_MS,
                DEFAULT_END_PADDING_MS,
                DEFAULT_NEXT_SPEECH_GAP_MS,
                DEFAULT_MAXIMUM_CHARACTERS_PER_SECOND
        );
    }

    public int maximumCharactersPerCue() {
        return Math.multiplyExact(maximumCharactersPerLine, maximumLines);
    }

    private static boolean inRange(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }
}
