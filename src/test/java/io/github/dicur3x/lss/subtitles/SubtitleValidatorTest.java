package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static SubtitleCue cue(long id, long start, long end, String text) {
        return new SubtitleCue(id, Duration.ofMillis(start), Duration.ofMillis(end), text, List.of());
    }
}
