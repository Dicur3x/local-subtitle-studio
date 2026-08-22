package io.github.dicur3x.lss.subtitles;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperCppTranscriberTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void requestsFullJsonWordTimingAndSileroVad() throws Exception {
        Path audio = Files.write(temporaryDirectory.resolve("audio.wav"), new byte[]{1});
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

        assertEquals("en", result.language());
        assertEquals(1, result.segments().size());
        List<String> command = invoked.get();
        assertTrue(command.contains("--output-json-full"));
        assertEquals("ru", command.get(command.indexOf("--language") + 1));
        assertTrue(command.contains("--print-progress"));
        assertTrue(command.contains("--split-on-word"));
        assertTrue(command.contains("--vad"));
        assertTrue(command.contains("--vad-model"));
        Path output = Path.of(command.get(command.indexOf("--output-file") + 1) + ".json");
        assertFalse(Files.exists(output));
    }
}
