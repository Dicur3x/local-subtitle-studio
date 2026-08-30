package io.github.dicur3x.lss.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.DefaultExternalProcessRunner;
import io.github.dicur3x.lss.subtitles.SubtitleCue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("manual")
class RealLocalTranslationTest {
    @Test
    void translatesContextualDialogueInBothDirectionsWithoutChangingTiming() throws Exception {
        Path executable = configuredPath("lss.real.llama");
        Path model = configuredPath("lss.real.translation.model");
        assumeTrue(Files.isRegularFile(executable), "Set -PrealLlama to llama-cli.exe");
        assumeTrue(Files.isRegularFile(model), "Set -PrealTranslationModel to a GGUF model");

        SubtitleTranslationService service = new SubtitleTranslationService(
                new LlamaCppTranslationEngine(executable.toString(), model,
                        new DefaultExternalProcessRunner(), new ObjectMapper()));
        List<SubtitleCue> english = List.of(
                cue(1, 0, 2, "I never said he stole the money."),
                cue(2, 2, 4, "Then why did you call the police?"),
                cue(3, 4, 6, "Because the door was open, not because of Alex."),
                cue(4, 6, 8, "Keep your voice down. He can hear us."));

        TranslatedSubtitles russian = service.translate(
                english, "English", "Russian", () -> false, percent -> { });
        assertEquals(english.size(), russian.translatedCues().size());
        assertTrue(russian.translatedCues().stream()
                .allMatch(cue -> cue.originalText().matches(".*[А-Яа-яЁё].*")));
        assertTimingUnchanged(english, russian.translatedCues());

        TranslatedSubtitles englishAgain = service.translate(
                russian.translatedCues(), "Russian", "English", () -> false, percent -> { });
        assertTrue(englishAgain.translatedCues().stream()
                .allMatch(cue -> cue.originalText().matches(".*[A-Za-z].*")));
        assertTimingUnchanged(russian.translatedCues(), englishAgain.translatedCues());

        System.out.println("English → Russian: " + russian.translatedCues().stream()
                .map(SubtitleCue::originalText).toList());
        System.out.println("Russian → English: " + englishAgain.translatedCues().stream()
                .map(SubtitleCue::originalText).toList());
    }

    private static SubtitleCue cue(long id, long startSeconds, long endSeconds, String text) {
        return new SubtitleCue(id, Duration.ofSeconds(startSeconds), Duration.ofSeconds(endSeconds),
                text, List.of());
    }

    private static void assertTimingUnchanged(List<SubtitleCue> source, List<SubtitleCue> translated) {
        for (int index = 0; index < source.size(); index++) {
            assertEquals(source.get(index).id(), translated.get(index).id());
            assertEquals(source.get(index).start(), translated.get(index).start());
            assertEquals(source.get(index).end(), translated.get(index).end());
        }
    }

    private static Path configuredPath(String property) {
        String value = System.getProperty(property, "");
        return value.isBlank() ? Path.of("missing") : Path.of(value).toAbsolutePath().normalize();
    }
}
