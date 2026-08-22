package io.github.dicur3x.lss.subtitles;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperSubtitleCreationServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsSrtThroughFfmpegWhisperVadAndTimingPipeline() throws Exception {
        Path video = Files.write(temporaryDirectory.resolve("episode.mkv"), new byte[]{1});
        Path model = Files.write(temporaryDirectory.resolve("model.bin"), new byte[]{2});
        Path vad = Files.write(temporaryDirectory.resolve("vad.bin"), new byte[]{3});
        Path workingRoot = Files.createDirectory(temporaryDirectory.resolve("work"));
        ApplicationSettings settings = new ApplicationSettings(
                ApplicationSettings.CURRENT_SCHEMA_VERSION,
                "ffmpeg-test", "ffprobe-test", "whisper-test",
                model.toString(), vad.toString(), workingRoot.toString());
        List<List<String>> commands = new ArrayList<>();

        WhisperSubtitleCreationService service = new WhisperSubtitleCreationService(
                () -> settings,
                (command, cancellation) -> {
                    commands.add(command);
                    if ("ffmpeg-test".equals(command.getFirst())) {
                        Files.write(Path.of(command.getLast()), new byte[45]);
                    } else {
                        int outputIndex = command.indexOf("--output-file");
                        Files.writeString(Path.of(command.get(outputIndex + 1) + ".json"), """
                                {"result":{"language":"ru"},"transcription":[{
                                  "offsets":{"from":1000,"to":4000},"text":"Проверка субтитров",
                                  "tokens":[
                                    {"text":" Проверка","offsets":{"from":1100,"to":1500},"p":0.95},
                                    {"text":" субтитров","offsets":{"from":1500,"to":1800},"p":0.94}
                                  ]
                                }]}
                                """);
                    }
                    return new ProcessResult(0, "", "");
                },
                new ObjectMapper());

        List<String> progress = new ArrayList<>();
        CreatedSubtitles created = service.create(video, 3, () -> false, progress::add);

        assertEquals("episode.ru.srt", created.file().getFileName().toString());
        assertEquals("ru", created.language());
        assertEquals(1, created.cueCount());
        assertTrue(Files.readString(created.file()).contains("00:00:01,050 --> 00:00:02,000"));
        assertEquals(List.of(
                "Preparing speech-recognition audio…",
                "Recognizing speech locally with whisper.cpp…",
                "Optimizing subtitle timing…"), progress);
        assertEquals(2, commands.size());
        assertTrue(commands.getFirst().contains("0:3"));
        try (var children = Files.list(workingRoot)) {
            assertFalse(children.findAny().isPresent());
        }
    }
}
