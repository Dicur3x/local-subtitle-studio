package io.github.dicur3x.lss.subtitles;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import io.github.dicur3x.lss.settings.SubtitlePreferences;
import io.github.dicur3x.lss.settings.OutputPreferences;
import io.github.dicur3x.lss.settings.UiLanguage;
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
                model.toString(), vad.toString(), workingRoot.toString(), SubtitlePreferences.defaults(),
                OutputPreferences.defaults(), UiLanguage.ENGLISH);
        List<List<String>> commands = new ArrayList<>();

        WhisperSubtitleCreationService service = new WhisperSubtitleCreationService(
                () -> settings,
                (command, cancellation) -> {
                    commands.add(command);
                    if ("ffmpeg-test".equals(command.getFirst())) {
                        TestAudioFiles.writeCanonicalWav(Path.of(command.getLast()), 5);
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

        List<PipelineProgress> progress = new ArrayList<>();
        CreatedSubtitles created = service.create(
                video, 3, "ru", DialogueAudioMode.MIXED_VOICE_OVER, () -> false, progress::add);

        assertEquals("episode.ru.srt", created.file().getFileName().toString());
        assertEquals("ru", created.language());
        assertEquals(1, created.cueCount());
        assertTrue(created.warnings().stream()
                .anyMatch(warning -> warning.type() == SubtitleWarningType.MIXED_VOICE_OVER));
        assertTrue(Files.readString(created.file()).contains("00:00:00,950 --> 00:00:04,200"));
        assertEquals(PipelineStage.PREPARING_AUDIO, progress.getFirst().stage());
        assertEquals(PipelineStage.COMPLETE, progress.getLast().stage());
        assertEquals(100, progress.getLast().overallPercent());
        assertTrue(progress.stream().anyMatch(value -> value.stage() == PipelineStage.TRANSCRIBING));
        assertTrue(progress.stream().anyMatch(value -> value.stage() == PipelineStage.VALIDATING));
        assertEquals(2, commands.size());
        assertTrue(commands.getFirst().contains("0:3"));
        assertEquals("ru", commands.getLast().get(commands.getLast().indexOf("--language") + 1));
        try (var children = Files.list(workingRoot)) {
            assertFalse(children.findAny().isPresent());
        }
    }

    @Test
    void reportsMissingRecognitionSetupBeforeStartingAProcess() {
        ApplicationSettings settings = ApplicationSettings.defaults();
        WhisperSubtitleCreationService service = new WhisperSubtitleCreationService(
                () -> settings,
                (command, cancellation) -> {
                    throw new AssertionError("Readiness must not start external processes");
                },
                new ObjectMapper());

        SubtitleReadiness readiness = service.readiness();

        assertFalse(readiness.ready());
        assertTrue(readiness.problems().stream().anyMatch(problem -> problem.contains("whisper.cpp")));
        assertTrue(readiness.problems().stream().anyMatch(problem -> problem.contains("Whisper model")));
    }
}
