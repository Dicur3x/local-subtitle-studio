package io.github.dicur3x.lss.translation;

import io.github.dicur3x.lss.subtitles.SubtitleCue;
import io.github.dicur3x.lss.subtitles.TokenTiming;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleTranslationServiceTest {
    @Test
    void translatesContextualBatchesWhilePreservingIdsAndTimestamps() throws Exception {
        List<TranslationBatch> batches = new ArrayList<>();
        TranslationEngine engine = (batch, cancellationRequested) -> {
            batches.add(batch);
            return new TranslationBatchResult(batch.requestedIds().stream()
                    .map(id -> new TranslatedText(id, "Перевод " + id)).toList());
        };
        List<Integer> progress = new ArrayList<>();
        List<SubtitleCue> original = java.util.stream.LongStream.rangeClosed(1, 8)
                .mapToObj(SubtitleTranslationServiceTest::cue).toList();

        TranslatedSubtitles result = new SubtitleTranslationService(engine, 3, 1)
                .translate(original, "en", "ru", () -> false, progress::add);

        assertEquals(List.of(0, 33, 66, 100), progress);
        assertEquals(3, batches.size());
        assertEquals(List.of(1L, 2L, 3L, 4L), ids(batches.get(0).cues()));
        assertEquals(List.of(1L, 2L, 3L), batches.get(0).requestedIds());
        assertEquals(List.of(3L, 4L, 5L, 6L, 7L), ids(batches.get(1).cues()));
        assertEquals(List.of(4L, 5L, 6L), batches.get(1).requestedIds());
        assertEquals(List.of(6L, 7L, 8L), ids(batches.get(2).cues()));
        assertEquals(List.of(7L, 8L), batches.get(2).requestedIds());
        for (int index = 0; index < original.size(); index++) {
            SubtitleCue source = original.get(index);
            SubtitleCue translated = result.translatedCues().get(index);
            assertEquals(source.id(), translated.id());
            assertEquals(source.start(), translated.start());
            assertEquals(source.end(), translated.end());
            assertEquals("Перевод " + source.id(), translated.originalText());
            assertTrue(translated.tokens().isEmpty(), "Source-language word timings must not be reused");
        }
        assertEquals("Cue 1\nПеревод 1", result.bilingualCues().getFirst().originalText());
    }

    @Test
    void refusesAResultThatOmitsARequestedCue() {
        TranslationEngine engine = (batch, cancellationRequested) -> new TranslationBatchResult(
                List.of(new TranslatedText(batch.requestedIds().getFirst(), "Only one")));

        TranslationException exception = assertThrows(TranslationException.class,
                () -> new SubtitleTranslationService(engine, 3, 0).translate(
                        List.of(cue(1), cue(2)), "en", "ru", () -> false, ignored -> { }));

        assertTrue(exception.getMessage().contains("omitted cue IDs"));
    }

    @Test
    void refusesUnexpectedOrDuplicateCueIds() {
        TranslationEngine unexpected = (batch, cancellationRequested) -> new TranslationBatchResult(
                List.of(new TranslatedText(99, "Wrong")));
        assertThrows(TranslationException.class,
                () -> new SubtitleTranslationService(unexpected).translate(
                        List.of(cue(1)), "en", "ru", () -> false, ignored -> { }));

        TranslationEngine duplicate = (batch, cancellationRequested) -> new TranslationBatchResult(
                List.of(new TranslatedText(1, "One"), new TranslatedText(1, "Again")));
        assertThrows(TranslationException.class,
                () -> new SubtitleTranslationService(duplicate).translate(
                        List.of(cue(1)), "en", "ru", () -> false, ignored -> { }));
    }

    @Test
    void supportsCancellationBeforeStartingAnEngineBatch() {
        TranslationEngine engine = (batch, cancellationRequested) -> {
            throw new AssertionError("Engine must not start after cancellation");
        };

        assertThrows(CancellationException.class,
                () -> new SubtitleTranslationService(engine).translate(
                        List.of(cue(1)), "en", "ru", () -> true, ignored -> { }));
    }

    private static List<Long> ids(List<TranslationCueInput> cues) {
        return cues.stream().map(TranslationCueInput::id).toList();
    }

    private static SubtitleCue cue(long id) {
        Duration start = Duration.ofSeconds(id * 2);
        return new SubtitleCue(
                id, start, start.plusSeconds(1), "Cue " + id,
                List.of(new TokenTiming("Cue", start, start.plusMillis(400), 0.9)));
    }
}
