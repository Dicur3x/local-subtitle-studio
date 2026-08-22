package io.github.dicur3x.lss.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonSettingsRepositoryTest {
    @TempDir
    Path tempDirectory;

    @Test
    void returnsEmptyWhenSettingsHaveNotBeenSaved() throws Exception {
        var repository = new JsonSettingsRepository(
                tempDirectory.resolve("settings.json"), new ObjectMapper());

        assertFalse(repository.load().isPresent());
    }

    @Test
    void savesAndLoadsNormalizedSettingsAtomically() throws Exception {
        Path settingsFile = tempDirectory.resolve("nested").resolve("settings.json");
        var repository = new JsonSettingsRepository(settingsFile, new ObjectMapper());
        var settings = new ApplicationSettings(
                1,
                "  C:\\Tools\\ffmpeg.exe  ",
                "C:\\Tools\\ffprobe.exe",
                "",
                "",
                "C:\\Temp"
        );

        repository.save(settings);

        assertEquals(settings, repository.load().orElseThrow());
        assertEquals("C:\\Tools\\ffmpeg.exe", repository.load().orElseThrow().ffmpegExecutable());
        try (var files = Files.list(settingsFile.getParent())) {
            assertEquals(1, files.count(), "temporary settings file was left behind");
        }
    }
}
