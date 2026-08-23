package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleValidatorTest {
    private final SubtitlePreferences preferences = SubtitlePreferences.defaults();

    @Test
    void reportsReadingSpeedWithoutRejectingAnOtherwiseValidSrt() throws Exception {
        SubtitleCue fast = cue(1, 0, 500, "This subtitle contains far too much text for half a second");

        SubtitleValidationReport report = new SubtitleValidator(preferences).validate(List.of(fast));

        assertFalse(report.warnings().isEmpty());
    }

    @Test
    void rejectsOverlappingCues() {
        SubtitleCue first = cue(1, 0, 1_000, "First");
        SubtitleCue second = cue(2, 900, 1_500, "Second");

        assertThrows(SubtitleCreationException.class,
                () -> new SubtitleValidator(preferences).validate(List.of(first, second)));
    }

    @Test
    void reportsLowConfidenceWithoutRewritingDialogue() throws Exception {
        List<TokenTiming> tokens = List.of(
                new TokenTiming(" strange", Duration.ZERO, Duration.ofMillis(300), 0.1),
                new TokenTiming(" phrase", Duration.ofMillis(300), Duration.ofMillis(600), 0.2));
        SubtitleCue uncertain = new SubtitleCue(
                1, Duration.ZERO, Duration.ofSeconds(2), "strange phrase", tokens);

        SubtitleValidationReport report = new SubtitleValidator(preferences).validate(List.of(uncertain));

        assertTrue(report.warnings().stream()
                .anyMatch(warning -> warning.type() == SubtitleWarningType.LOW_CONFIDENCE));
    }

    private static SubtitleCue cue(long id, long start, long end, String text) {
        return new SubtitleCue(id, Duration.ofMillis(start), Duration.ofMillis(end), text, List.of());
    }
}
