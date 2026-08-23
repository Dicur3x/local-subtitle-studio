package io.github.dicur3x.lss.subtitles;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.DefaultExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import io.github.dicur3x.lss.settings.JsonSettingsRepository;
import io.github.dicur3x.lss.settings.SettingsManager;
import io.github.dicur3x.lss.settings.SettingsPaths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("manual")
class RealMediaWhisperPipelineTest {
    private static final Pattern FIRST_START = Pattern.compile("(?m)^00:00:(\\d{2}),(\\d{3}) -->");

    @TempDir
    Path temporaryDirectory;

    @Test
    void firstTwoMinutesStayOnTheOriginalTimelineWithVad() throws Exception {
        String configuredMedia = System.getProperty("lss.real.media", "");
        assumeTrue(!configuredMedia.isBlank(), "Run with -PrealMedia=<path>");
        Path media = Path.of(configuredMedia);
        assumeTrue(Files.isRegularFile(media), "Real media file is not available");

        ObjectMapper objectMapper = new ObjectMapper();
        SettingsManager settingsManager = new SettingsManager(new JsonSettingsRepository(
                SettingsPaths.defaultSettingsFile(), objectMapper));
        ApplicationSettings settings = settingsManager.current();
        ExternalProcessRunner processRunner = new DefaultExternalProcessRunner();
        Path sample = temporaryDirectory.resolve("first-120s.wav");
        ProcessResult extraction = processRunner.run(List.of(
                settings.ffmpegExecutable(), "-hide_banner", "-loglevel", "error", "-y",
                "-i", media.toString(), "-map", "0:a:0", "-t", "120",
                "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", sample.toString()
        ), () -> false);
        assertEquals(0, extraction.exitCode(), extraction.standardError());

        CreatedSubtitles created = new WhisperSubtitleCreationService(
                settingsManager::current, processRunner, objectMapper)
                .create(sample, 0, "ru", () -> false, progress -> { });

        String srt = Files.readString(created.file());
        Matcher matcher = FIRST_START.matcher(srt);
        assertTrue(matcher.find(), "First SRT timestamp was not found");
        long firstStartMillis = Long.parseLong(matcher.group(1)) * 1_000L
                + Long.parseLong(matcher.group(2));
        assertTrue(firstStartMillis >= 2_000 && firstStartMillis <= 2_600,
                "Expected the first Brooklyn cue near 2.3 s, got " + firstStartMillis + " ms");
    }
}
