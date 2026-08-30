package io.github.dicur3x.lss.translation;

import io.github.dicur3x.lss.settings.ApplicationSettings;
import io.github.dicur3x.lss.settings.SubtitlePreferences;
import io.github.dicur3x.lss.subtitles.CreatedSubtitles;
import io.github.dicur3x.lss.subtitles.SubtitleCue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSubtitleTranslationWorkflowTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void translatesAndWritesSeparateSafeVariantsWithProgress() throws Exception {
        Path llama = Files.createFile(temporaryDirectory.resolve("llama-cli.exe"));
        Path model = Files.createFile(temporaryDirectory.resolve("translation.gguf"));
        Path original = Files.writeString(temporaryDirectory.resolve("film.ru.srt"), "original");
        ApplicationSettings settings = ApplicationSettings.defaults()
                .withManagedLlama(llama.toString())
                .withManagedTranslationModel(model.toString());
        TranslationEngine engine = (batch, cancellationRequested) -> new TranslationBatchResult(
                batch.requestedIds().stream()
                        .map(id -> new TranslatedText(id, "Translation " + id)).toList());
        LocalSubtitleTranslationWorkflow workflow = new LocalSubtitleTranslationWorkflow(
                () -> settings, ignored -> engine);
        List<SubtitleCue> cues = List.of(
                cue(1, "Первая реплика"), cue(2, "Вторая реплика"));
        CreatedSubtitles created = new CreatedSubtitles(
                original, "ru", cues.size(), List.of(), cues, List.of(),
                SubtitlePreferences.defaults());
        List<Integer> progress = new ArrayList<>();

        CreatedTranslations result = workflow.translate(
                created, "en", () -> false, progress::add);

        assertEquals("film.en.srt", result.translatedFile().getFileName().toString());
        assertEquals(List.of(0, 100), progress);
        assertTrue(Files.readString(result.translatedFile()).contains("Translation 1"));
        assertEquals("original", Files.readString(original));
    }

    @Test
    void reportsMissingLocalTranslationComponents() {
        LocalSubtitleTranslationWorkflow workflow = new LocalSubtitleTranslationWorkflow(
                ApplicationSettings::defaults,
                ignored -> (batch, cancellationRequested) -> {
                    throw new AssertionError("The engine must not be started");
                });

        assertFalse(workflow.readiness().ready());
        assertTrue(workflow.readiness().problems().stream()
                .anyMatch(problem -> problem.contains("llama.cpp")));
        assertTrue(workflow.readiness().problems().stream()
                .anyMatch(problem -> problem.contains("translation model")));
    }

    @Test
    void reportsAnInvalidExecutablePathWithoutCrashingTheUiCheck() throws Exception {
        Path model = Files.createFile(temporaryDirectory.resolve("translation.gguf"));
        ApplicationSettings settings = ApplicationSettings.defaults()
                .withManagedLlama("invalid\0path")
                .withManagedTranslationModel(model.toString());
        LocalSubtitleTranslationWorkflow workflow = new LocalSubtitleTranslationWorkflow(
                () -> settings,
                ignored -> (batch, cancellationRequested) -> {
                    throw new AssertionError("The engine must not be started");
                });

        assertFalse(workflow.readiness().ready());
        assertTrue(workflow.readiness().problems().stream()
                .anyMatch(problem -> problem.contains("llama.cpp")));
    }

    private static SubtitleCue cue(long id, String text) {
        Duration start = Duration.ofSeconds(id * 2);
        return new SubtitleCue(id, start, start.plusSeconds(1), text, List.of());
    }
}
