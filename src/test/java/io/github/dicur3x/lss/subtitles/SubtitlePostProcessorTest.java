package io.github.dicur3x.lss.subtitles;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubtitlePostProcessorTest {
    @Test
    void mergesOnlyImmediatelyAdjacentIdenticalCuesAndRenumbersTheRest() {
        List<SubtitleCue> source = List.of(
                cue(1, 0, 1_000, "Повтор"),
                cue(2, 1_100, 2_000, "  повтор "),
                cue(3, 3_000, 4_000, "Повтор"));

        List<SubtitleCue> result = new SubtitlePostProcessor().process(source);

        assertEquals(2, result.size());
        assertEquals(Duration.ofMillis(2_000), result.getFirst().end());
        assertEquals(2, result.getLast().id());
        assertEquals(Duration.ofMillis(3_000), result.getLast().start());
    }

    private static SubtitleCue cue(long id, long start, long end, String text) {
        return new SubtitleCue(id, Duration.ofMillis(start), Duration.ofMillis(end), text, List.of());
    }
}
