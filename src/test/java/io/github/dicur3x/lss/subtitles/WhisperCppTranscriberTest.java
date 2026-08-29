package io.github.dicur3x.lss.subtitles;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperCppTranscriberTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void requestsFullJsonWordTimingAndSileroVad() throws Exception {
        Path audio = TestAudioFiles.writeCanonicalWav(temporaryDirectory.resolve("audio.wav"), 2);
        Path model = Files.write(temporaryDirectory.resolve("model.bin"), new byte[]{2});
        Path vad = Files.write(temporaryDirectory.resolve("vad.bin"), new byte[]{3});
        AtomicReference<List<String>> invoked = new AtomicReference<>();

        WhisperCppTranscriber transcriber = new WhisperCppTranscriber(
                "whisper-cli", model, vad,
                (command, cancellation) -> {
                    invoked.set(command);
                    int outputIndex = command.indexOf("--output-file");
                    Path json = Path.of(command.get(outputIndex + 1) + ".json");
                    Files.writeString(json, """
                            {"result":{"language":"en"},"transcription":[{
                              "offsets":{"from":100,"to":600},"text":"Hello",
                              "tokens":[{"text":" Hello","offsets":{"from":120,"to":580},"p":0.9}]
                            }]}
                            """);
                    return new ProcessResult(0, "", "");
                },
                new WhisperJsonParser(new ObjectMapper()));

        TranscriptionResult result = transcriber.transcribe(audio, "ru", () -> false, percent -> { });

        assertEquals("ru", result.language());
        assertEquals(1, result.segments().size());
        List<String> command = invoked.get();
        assertTrue(command.contains("--output-json-full"));
        assertEquals("ru", command.get(command.indexOf("--language") + 1));
        assertTrue(command.contains("--print-progress"));
        assertTrue(command.contains("--split-on-word"));
        assertEquals("64", command.get(command.indexOf("--max-context") + 1));
        assertTrue(command.contains("--vad"));
        assertTrue(command.contains("--vad-model"));
        Path output = Path.of(command.get(command.indexOf("--output-file") + 1) + ".json");
        assertFalse(Files.exists(output));
    }

    @Test
    void mergesOverlappingChunksOnTheOriginalTimelineAndRemovesTemporaryFiles() throws Exception {
        Path audio = TestAudioFiles.writeCanonicalWav(temporaryDirectory.resolve("long-audio.wav"), 10);
        Path model = Files.write(temporaryDirectory.resolve("model.bin"), new byte[]{2});
        Path vad = Files.write(temporaryDirectory.resolve("vad.bin"), new byte[]{3});
        List<Path> chunkFiles = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        WhisperCppTranscriber transcriber = new WhisperCppTranscriber(
                "whisper-cli", model, vad,
                (command, cancellation) -> {
                    calls.incrementAndGet();
                    Path chunk = Path.of(command.get(command.indexOf("--file") + 1));
                    chunkFiles.add(chunk);
                    int outputIndex = command.indexOf("--output-file");
                    Path json = Path.of(command.get(outputIndex + 1) + ".json");
                    Files.writeString(json, """
                            {"result":{"language":"ru"},"transcription":[{
                              "offsets":{"from":1000,"to":2000},"text":"Реплика",
                              "tokens":[{"text":" Реплика","offsets":{"from":1100,"to":1900},"p":0.9}]
                            }]}
                            """);
                    return new ProcessResult(0, "", "progress = 100%");
                },
                new WhisperJsonParser(new ObjectMapper()),
                new PcmWavChunker(Duration.ofSeconds(4), Duration.ofSeconds(1)));

        List<Integer> progress = new ArrayList<>();
        TranscriptionResult result = transcriber.transcribe(
                audio, "ru", () -> false, progress::add);

        assertEquals(3, calls.get());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(8)),
                result.segments().stream().map(RecognizedSegment::speechStart).toList());
        assertEquals(List.of(1L, 2L, 3L),
                result.segments().stream().map(RecognizedSegment::id).toList());
        assertTrue(progress.stream().reduce(0, (previous, current) -> {
            assertTrue(current >= previous, "Progress must never move backwards");
            return current;
        }) == 100);
        assertTrue(chunkFiles.stream().noneMatch(Files::exists));
        assertTrue(Files.exists(audio), "The source prepared WAV is owned by the caller");
    }

    @Test
    void retriesSuspiciousRecognitionWithCleanContext() throws Exception {
        Path audio = TestAudioFiles.writeCanonicalWav(temporaryDirectory.resolve("audio.wav"), 2);
        Path model = Files.write(temporaryDirectory.resolve("model.bin"), new byte[]{2});
        Path vad = Files.write(temporaryDirectory.resolve("vad.bin"), new byte[]{3});
        List<Integer> contexts = new ArrayList<>();

        WhisperCppTranscriber transcriber = new WhisperCppTranscriber(
                "whisper-cli", model, vad,
                (command, cancellation) -> {
                    int context = Integer.parseInt(command.get(command.indexOf("--max-context") + 1));
                    contexts.add(context);
                    Path json = Path.of(command.get(command.indexOf("--output-file") + 1) + ".json");
                    Files.writeString(json, context == 64 ? transcription(0, 50_000, "Застрявшая фраза")
                            : transcription(200, 1_200, "Нормальная реплика"));
                    return new ProcessResult(0, "", "progress = 100%");
                },
                new WhisperJsonParser(new ObjectMapper()));

        TranscriptionResult result = transcriber.transcribe(audio, "ru", () -> false, percent -> { });

        assertEquals(List.of(64, 0), contexts);
        assertEquals("Нормальная реплика", result.segments().getFirst().text());
    }

    @Test
    void refusesToReturnARepeatedTranscriptAfterTheAutomaticRetry() throws Exception {
        Path audio = TestAudioFiles.writeCanonicalWav(temporaryDirectory.resolve("audio.wav"), 2);
        Path model = Files.write(temporaryDirectory.resolve("model.bin"), new byte[]{2});
        Path vad = Files.write(temporaryDirectory.resolve("vad.bin"), new byte[]{3});
        AtomicInteger calls = new AtomicInteger();

        WhisperCppTranscriber transcriber = new WhisperCppTranscriber(
                "whisper-cli", model, vad,
                (command, cancellation) -> {
                    calls.incrementAndGet();
                    Path json = Path.of(command.get(command.indexOf("--output-file") + 1) + ".json");
                    Files.writeString(json, transcription(0, 50_000, "Застрявшая фраза"));
                    return new ProcessResult(0, "", "progress = 100%");
                },
                new WhisperJsonParser(new ObjectMapper()));

        RecognitionLoopException exception = assertThrows(RecognitionLoopException.class,
                () -> transcriber.transcribe(audio, "ru", () -> false, percent -> { }));

        assertEquals(Duration.ZERO, exception.position());
        assertEquals(2, calls.get());
    }

    private static String transcription(long from, long to, String text) {
        return """
                {"result":{"language":"ru"},"transcription":[{
                  "offsets":{"from":%d,"to":%d},"text":"%s","tokens":[]
                }]}
                """.formatted(from, to, text);
    }
}
