package io.github.dicur3x.lss.subtitles;

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
}
