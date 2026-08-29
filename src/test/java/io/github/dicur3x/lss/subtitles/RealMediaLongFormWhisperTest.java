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
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("manual")
class RealMediaLongFormWhisperTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recognizesPastTheOldRepetitionPointAcrossChunkBoundaries() throws Exception {
        String configuredMedia = System.getProperty("lss.real.media", "");
        assumeTrue(!configuredMedia.isBlank(), "Run with -PrealMedia=<path>");
        Path media = Path.of(configuredMedia);
        assumeTrue(Files.isRegularFile(media), "Real media file is not available");
        int durationSeconds = Integer.parseInt(System.getProperty("lss.real.duration.seconds", "1020"));
        int tailToleranceSeconds = Integer.parseInt(
                System.getProperty("lss.real.tail.tolerance.seconds", "90"));
        assumeTrue(durationSeconds > PcmWavChunker.DEFAULT_CORE_DURATION.toSeconds(),
                "The long-form check must cross at least one recognition chunk boundary");

        ObjectMapper objectMapper = new ObjectMapper();
        SettingsManager settingsManager = new SettingsManager(new JsonSettingsRepository(
                SettingsPaths.defaultSettingsFile(), objectMapper));
        ApplicationSettings loaded = settingsManager.current();
        String configuredModel = System.getProperty("lss.real.model", "").strip();
        ApplicationSettings settings = configuredModel.isEmpty()
                ? loaded
                : loaded.withManagedModels(configuredModel, loaded.whisperVadModel());
        ExternalProcessRunner processRunner = new DefaultExternalProcessRunner();
        Path sample = temporaryDirectory.resolve("long-form-sample.wav");
        ProcessResult extraction = processRunner.run(List.of(
                settings.ffmpegExecutable(), "-hide_banner", "-loglevel", "error", "-y",
                "-i", media.toString(), "-map", "0:a:0", "-t", Integer.toString(durationSeconds),
                "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le", sample.toString()
        ), () -> false);
        assertEquals(0, extraction.exitCode(), extraction.standardError());

        CreatedSubtitles created = new WhisperSubtitleCreationService(
                () -> settings, processRunner, objectMapper)
                .create(sample, 0, "ru", () -> false, progress -> { });

        assertFalse(created.cues().isEmpty());
        Duration lastCueEnd = created.cues().getLast().end();
        assertTrue(lastCueEnd.compareTo(Duration.ofSeconds(durationSeconds - (long) tailToleranceSeconds)) > 0,
                "Recognition stopped too early at " + lastCueEnd);
        assertTrue(lastCueEnd.compareTo(Duration.ofSeconds(durationSeconds + 2L)) <= 0,
                "Recognition escaped the source timeline at " + lastCueEnd);
        assertTrue(created.cues().stream().allMatch(cue ->
                        cue.end().minus(cue.start()).toMillis()
                                <= settings.subtitlePreferences().maximumDurationMs()),
                "A cue exceeded the configured maximum display duration");
        assertTrue(created.cues().stream().anyMatch(cue ->
                        cue.start().compareTo(PcmWavChunker.DEFAULT_CORE_DURATION) < 0
                                && cue.end().compareTo(PcmWavChunker.DEFAULT_CORE_DURATION) > 0
                        || cue.start().compareTo(PcmWavChunker.DEFAULT_CORE_DURATION) >= 0),
                "No cue survived beyond the first recognition chunk");

        String report = System.getProperty("lss.real.report", "").strip();
        if (!report.isEmpty()) {
            Path destination = Path.of(report).toAbsolutePath().normalize();
            Files.createDirectories(destination.getParent());
            Files.copy(created.file(), destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
