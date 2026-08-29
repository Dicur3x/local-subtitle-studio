package io.github.dicur3x.lss.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.subtitles.DialogueAudioMode;
import io.github.dicur3x.lss.subtitles.RecognitionChunkKey;
import io.github.dicur3x.lss.subtitles.RecognizedSegment;
import io.github.dicur3x.lss.subtitles.TokenTiming;
import io.github.dicur3x.lss.subtitles.TranscriptionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresPreparedAudioAndCompletedRecognitionChunks() throws Exception {
        Path media = Files.write(temporaryDirectory.resolve("episode.mkv"), new byte[]{1, 2, 3});
        Path storage = temporaryDirectory.resolve("storage");
        RecoveryStore store = new RecoveryStore(() -> storage, new ObjectMapper());
        RecoveryStore.RecoverySession session = store.open(
                media, 2, "ru", DialogueAudioMode.STANDARD, "profile-a");
        Files.write(session.audioFile(), new byte[45]);
        RecognitionChunkKey key = new RecognitionChunkKey(
                0, Duration.ZERO, Duration.ZERO, Duration.ofMinutes(8));
        TranscriptionResult partial = new TranscriptionResult("ru", List.of(
                new RecognizedSegment(1, Duration.ofSeconds(1), Duration.ofSeconds(2), "Привет",
                        List.of(new TokenTiming(" Привет", Duration.ofSeconds(1),
                                Duration.ofSeconds(2), 0.95)))));
        session.save(key, partial);

        RecoveryStore reopened = new RecoveryStore(() -> storage, new ObjectMapper());
        RecoveryJob job = reopened.activeJob().orElseThrow();
        RecoveryStore.RecoverySession restored = reopened.open(
                media, 2, "ru", DialogueAudioMode.STANDARD, "profile-a");

        assertEquals("episode.mkv", job.displayName());
        assertEquals(1, job.completedChunks());
        assertTrue(reopened.sourceIsUnchanged(job));
        assertTrue(restored.hasPreparedAudio());
        assertEquals("Привет", restored.load(key).orElseThrow().segments().getFirst().text());
    }

    @Test
    void changedSourceIsNotOfferedAsRecoverable() throws Exception {
        Path media = Files.write(temporaryDirectory.resolve("film.mkv"), new byte[]{1});
        RecoveryStore store = new RecoveryStore(
                () -> temporaryDirectory.resolve("storage"), new ObjectMapper());
        store.open(media, 1, "en", DialogueAudioMode.STANDARD, "profile");
        RecoveryJob job = store.activeJob().orElseThrow();

        Files.write(media, new byte[]{1, 2});

        assertFalse(store.sourceIsUnchanged(job));
    }

    @Test
    void successfulCompletionRemovesOnlyTheRecoveryWorkspace() throws Exception {
        Path media = Files.write(temporaryDirectory.resolve("film.mkv"), new byte[]{1});
        Path storage = temporaryDirectory.resolve("storage");
        Path neighbour = Files.createDirectories(storage).resolve("keep.txt");
        Files.writeString(neighbour, "keep");
        RecoveryStore store = new RecoveryStore(() -> storage, new ObjectMapper());
        RecoveryStore.RecoverySession session = store.open(
                media, 1, "en", DialogueAudioMode.STANDARD, "profile");

        session.complete();

        assertTrue(Files.isRegularFile(neighbour));
        assertTrue(store.activeJob().isEmpty());
    }
}
