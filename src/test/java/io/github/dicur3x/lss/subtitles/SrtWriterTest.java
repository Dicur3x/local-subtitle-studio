package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.OutputLocation;
import io.github.dicur3x.lss.settings.OutputPreferences;
import io.github.dicur3x.lss.settings.SubtitlePreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrtWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesBesideVideoWithoutOverwritingExistingSubtitles() throws Exception {
        Path video = Files.write(temporaryDirectory.resolve("film.mkv"), new byte[]{1});
        Path existing = Files.writeString(temporaryDirectory.resolve("film.ru.srt"), "keep me");
        SubtitleCue cue = new SubtitleCue(1, Duration.ofMillis(950), Duration.ofMillis(1_900),
                "Очень длинная строка субтитра переносится на две читаемые строки рядом с видео",
                List.of());

        Path created = new SrtWriter().write(video, "ru", List.of(cue));

        assertEquals("film.ru.2.srt", created.getFileName().toString());
        assertEquals("keep me", Files.readString(existing));
        String contents = Files.readString(created);
        assertTrue(contents.contains("00:00:00,950 --> 00:00:01,900"));
        assertTrue(contents.contains("\r\n"));
        assertTrue(contents.endsWith("\r\n\r\n"));
    }

    @Test
    void createsSubsFolderWhenSelected() throws Exception {
        Path video = Files.write(temporaryDirectory.resolve("episode.mkv"), new byte[]{1});
        SubtitleCue cue = new SubtitleCue(1, Duration.ZERO, Duration.ofSeconds(1), "Текст", List.of());

        Path created = new SrtWriter(SubtitlePreferences.defaults(),
                new OutputPreferences(OutputLocation.SUBS_FOLDER, ""))
                .write(video, "ru", List.of(cue));

        assertEquals(temporaryDirectory.resolve("Subs").toAbsolutePath(), created.getParent());
        assertEquals("episode.ru.srt", created.getFileName().toString());
    }

    @Test
    void savesToChosenFolderWhenSelected() throws Exception {
        Path video = Files.write(temporaryDirectory.resolve("episode.mkv"), new byte[]{1});
        Path custom = temporaryDirectory.resolve("exported");
        SubtitleCue cue = new SubtitleCue(1, Duration.ZERO, Duration.ofSeconds(1), "Text", List.of());

        Path created = new SrtWriter(SubtitlePreferences.defaults(),
                new OutputPreferences(OutputLocation.CUSTOM_FOLDER, custom.toString()))
                .write(video, "en", List.of(cue));

        assertEquals(custom.toAbsolutePath(), created.getParent());
        assertTrue(Files.isRegularFile(created));
    }
}
