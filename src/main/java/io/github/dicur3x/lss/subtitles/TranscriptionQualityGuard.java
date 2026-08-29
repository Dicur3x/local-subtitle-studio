package io.github.dicur3x.lss.subtitles;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class TranscriptionQualityGuard {
    private static final Duration MAXIMUM_SHORT_TEXT_DURATION = Duration.ofSeconds(40);
    private static final Duration MAXIMUM_REPETITION_SPAN = Duration.ofSeconds(25);

    private TranscriptionQualityGuard() {
    }

    static Optional<String> suspiciousRepetition(List<RecognizedSegment> segments) {
        for (RecognizedSegment segment : segments) {
            Duration duration = segment.speechEnd().minus(segment.speechStart());
            if (duration.compareTo(MAXIMUM_SHORT_TEXT_DURATION) > 0
                    && normalize(segment.text()).length() <= 60) {
                return Optional.of(segment.text());
            }
        }

        int runStart = 0;
        for (int index = 1; index <= segments.size(); index++) {
            boolean sameAsPrevious = index < segments.size()
                    && normalize(segments.get(index).text())
                    .equals(normalize(segments.get(index - 1).text()));
            if (sameAsPrevious) {
                continue;
            }
            int runLength = index - runStart;
            if (runLength >= 3) {
                RecognizedSegment first = segments.get(runStart);
                RecognizedSegment last = segments.get(index - 1);
                if (last.speechEnd().minus(first.speechStart())
                        .compareTo(MAXIMUM_REPETITION_SPAN) > 0) {
                    return Optional.of(first.text());
                }
            }
            runStart = index;
        }
        return Optional.empty();
    }

    private static String normalize(String text) {
        return text.replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip().toLowerCase(Locale.ROOT);
    }
}
