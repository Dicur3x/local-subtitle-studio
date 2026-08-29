package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatedSubtitlesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reviewRefreshesCueWarningsButKeepsTheResultWideVoiceOverNotice() {
        SubtitleCue cue = new SubtitleCue(
                1, Duration.ZERO, Duration.ofSeconds(2), "Исправлено", List.of());
        CreatedSubtitles created = new CreatedSubtitles(
                temporaryDirectory.resolve("film.ru.srt"), "ru", 1,
                List.of(
                        new SubtitleWarning(SubtitleWarningType.TOO_FAST, 1),
                        new SubtitleWarning(SubtitleWarningType.MIXED_VOICE_OVER, 1)),
                List.of(cue), List.of(new SubtitleIssue(SubtitleWarningType.TOO_FAST, 1)),
                SubtitlePreferences.defaults());

        CreatedSubtitles reviewed = created.afterReview(
                List.of(cue), new SubtitleValidationReport(List.of(), List.of()));

        assertTrue(reviewed.issues().isEmpty());
        assertEquals(List.of(new SubtitleWarning(SubtitleWarningType.MIXED_VOICE_OVER, 1)),
                reviewed.warnings());
    }
}
