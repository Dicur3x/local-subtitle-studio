package io.github.dicur3x.lss.subtitles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SubtitleTimingOptimizer {
    public static final Duration DEFAULT_START_PADDING = Duration.ofMillis(50);
    public static final Duration DEFAULT_END_PADDING = Duration.ofMillis(200);
    public static final Duration DEFAULT_MINIMUM_DURATION = Duration.ofMillis(800);
    public static final Duration DEFAULT_NEXT_SPEECH_GAP = Duration.ofMillis(100);

    private final Duration startPadding;
    private final Duration endPadding;
    private final Duration minimumDuration;
    private final Duration nextSpeechGap;

    public SubtitleTimingOptimizer() {
        this(DEFAULT_START_PADDING, DEFAULT_END_PADDING,
                DEFAULT_MINIMUM_DURATION, DEFAULT_NEXT_SPEECH_GAP);
    }

    public SubtitleTimingOptimizer(
            Duration startPadding,
            Duration endPadding,
            Duration minimumDuration,
            Duration nextSpeechGap
    ) {
        this.startPadding = requireNonNegative(startPadding, "startPadding");
        this.endPadding = requireNonNegative(endPadding, "endPadding");
        this.minimumDuration = requirePositive(minimumDuration, "minimumDuration");
        this.nextSpeechGap = requireNonNegative(nextSpeechGap, "nextSpeechGap");
    }

    public List<SubtitleCue> optimize(List<RecognizedSegment> recognized) {
        List<RecognizedSegment> segments = new ArrayList<>(Objects.requireNonNull(recognized, "recognized"));
        segments.sort(Comparator.comparing(RecognizedSegment::speechStart));
        List<SubtitleCue> cues = new ArrayList<>(segments.size());
        for (int index = 0; index < segments.size(); index++) {
            RecognizedSegment segment = segments.get(index);
            Duration start = subtractClamped(segment.speechStart(), startPadding);
            Duration paddedSpeechEnd = segment.speechEnd().plus(endPadding);
            Duration end = max(paddedSpeechEnd, start.plus(minimumDuration));
            if (index + 1 < segments.size()) {
                Duration latestEnd = subtractClamped(segments.get(index + 1).speechStart(), nextSpeechGap);
                if (latestEnd.compareTo(end) < 0) {
                    end = latestEnd;
                }
            }
            if (end.compareTo(start) <= 0) {
                end = start.plusMillis(1);
            }
            cues.add(new SubtitleCue(index + 1L, start, end, segment.text(), segment.tokens()));
        }
        return List.copyOf(cues);
    }

    private static Duration subtractClamped(Duration value, Duration amount) {
        Duration result = value.minus(amount);
        return result.isNegative() ? Duration.ZERO : result;
    }

    private static Duration max(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static Duration requireNonNegative(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration duration = requireNonNegative(value, name);
        if (duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
