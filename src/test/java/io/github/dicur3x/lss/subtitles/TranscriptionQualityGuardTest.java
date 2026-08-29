package io.github.dicur3x.lss.subtitles;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptionQualityGuardTest {
    @Test
    void catchesTheLongRepeatedPhraseSeenInTheResolutionSample() {
        List<RecognizedSegment> segments = List.of(
                segment(1, 0, 44_000, "Майкл, ты что?"),
                segment(2, 45_000, 105_000, "Майкл, ты что?"));

        assertTrue(TranscriptionQualityGuard.suspiciousRepetition(segments).isPresent());
    }

    @Test
    void acceptsOrdinaryShortDialogue() {
        List<RecognizedSegment> segments = List.of(
                segment(1, 0, 2_000, "Ты придёшь?"),
                segment(2, 3_000, 4_000, "Да."),
                segment(3, 5_000, 7_000, "Тогда пошли."));

        assertFalse(TranscriptionQualityGuard.suspiciousRepetition(segments).isPresent());
    }

    private static RecognizedSegment segment(long id, long start, long end, String text) {
        return new RecognizedSegment(id, Duration.ofMillis(start), Duration.ofMillis(end), text, List.of());
    }
}
