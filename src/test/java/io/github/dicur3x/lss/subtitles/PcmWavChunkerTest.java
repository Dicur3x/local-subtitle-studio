package io.github.dicur3x.lss.subtitles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcmWavChunkerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsOverlappingChunksWithNonOverlappingOwnershipWindows() throws Exception {
        Path source = TestAudioFiles.writeCanonicalWav(temporaryDirectory.resolve("long.wav"), 5);
        PcmWavChunker chunker = new PcmWavChunker(
                Duration.ofSeconds(2), Duration.ofMillis(500));

        List<RecognitionAudioChunk> chunks = chunker.split(source, () -> false);

        assertEquals(3, chunks.size());
        assertEquals(Duration.ZERO, chunks.getFirst().offset());
        assertEquals(Duration.ofMillis(1_500), chunks.get(1).offset());
        assertEquals(Duration.ofSeconds(2), chunks.get(1).keepFrom());
        assertEquals(Duration.ofSeconds(4), chunks.get(1).keepTo());
        assertTrue(chunks.stream().allMatch(chunk -> Files.isRegularFile(chunk.file())));

        PcmWavChunker.deleteTemporaryChunks(chunks);
        assertTrue(Files.isRegularFile(source));
        assertTrue(chunks.stream().noneMatch(chunk -> Files.exists(chunk.file())));
    }

    @Test
    void keepsShortAudioAsTheOriginalFile() throws Exception {
        Path source = TestAudioFiles.writeCanonicalWav(temporaryDirectory.resolve("short.wav"), 1);

        List<RecognitionAudioChunk> chunks = new PcmWavChunker(
                Duration.ofSeconds(2), Duration.ofMillis(500)).split(source, () -> false);

        assertEquals(1, chunks.size());
        assertEquals(source.toAbsolutePath(), chunks.getFirst().file());
        assertFalse(chunks.getFirst().temporary());
    }
}
