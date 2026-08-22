package io.github.dicur3x.lss.subtitles;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubtitleTimingOptimizerTest {
    private final SubtitleTimingOptimizer optimizer = new SubtitleTimingOptimizer();

    @Test
    void endsNearSpeechInsteadOfStickingUntilTheNextPhrase() {
        List<SubtitleCue> cues = optimizer.optimize(List.of(
                segment(1, 1_000, 1_700, "Первая реплика"),
                segment(2, 5_000, 5_800, "Вторая реплика")
        ));

        assertEquals(Duration.ofMillis(950), cues.getFirst().start());
        assertEquals(Duration.ofMillis(1_900), cues.getFirst().end());
    }

    @Test
    void preservesAMinimumReadableDurationWithoutCrossingNextSpeech() {
        List<SubtitleCue> cues = optimizer.optimize(List.of(
                segment(1, 2_000, 2_100, "Да"),
                segment(2, 2_600, 3_000, "Нет")
        ));

        assertEquals(Duration.ofMillis(1_950), cues.getFirst().start());
        assertEquals(Duration.ofMillis(2_500), cues.getFirst().end());
    }

    private static RecognizedSegment segment(long id, long start, long end, String text) {
        return new RecognizedSegment(
                id, Duration.ofMillis(start), Duration.ofMillis(end), text, List.of());
    }
}
