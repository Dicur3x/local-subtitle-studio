package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SrtWriter {
    private final SubtitleTextFormatter textFormatter;

    public SrtWriter() {
        this(SubtitlePreferences.defaults());
    }

    public SrtWriter(SubtitlePreferences preferences) {
        textFormatter = new SubtitleTextFormatter(preferences);
    }

    public Path write(Path mediaFile, String language, List<SubtitleCue> cues)
            throws SubtitleCreationException {
        Path media = Objects.requireNonNull(mediaFile, "mediaFile").toAbsolutePath().normalize();
        Path directory = media.getParent();
        if (directory == null || !Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new SubtitleCreationException("The video folder is not writable, so the SRT file cannot be saved.");
        }
        List<SubtitleCue> safeCues = List.copyOf(Objects.requireNonNull(cues, "cues"));
        if (safeCues.isEmpty()) {
            throw new SubtitleCreationException("No speech was recognized, so an empty SRT file was not created.");
        }

        String stem = fileStem(media.getFileName().toString());
        String languagePart = safeLanguage(language);
        String contents = render(safeCues);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, ".lss-subtitles-", ".tmp");
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            for (int copy = 1; copy < 10_000; copy++) {
                String suffix = copy == 1 ? "" : "." + copy;
                Path destination = directory.resolve(stem + "." + languagePart + suffix + ".srt");
                try {
                    moveWithoutReplacing(temporary, destination);
                    return destination;
                } catch (FileAlreadyExistsException ignored) {
                    // Keep trying a numbered name; existing subtitles are never overwritten.
                }
            }
            throw new SubtitleCreationException("Could not find a free filename for the SRT file.");
        } catch (SubtitleCreationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SubtitleCreationException("Could not save the SRT file beside the video.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The finished SRT, if any, has already been moved into place.
                }
            }
        }
    }

    String render(List<SubtitleCue> cues) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < cues.size(); index++) {
            SubtitleCue cue = cues.get(index);
            output.append(index + 1).append("\r\n")
                    .append(timestamp(cue.start())).append(" --> ").append(timestamp(cue.end()))
                    .append("\r\n")
                    .append(textFormatter.format(cue.originalText()))
                    .append("\r\n\r\n");
        }
        return output.toString();
    }

    private static String timestamp(Duration duration) {
        long millis = duration.toMillis();
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long seconds = (millis % 60_000) / 1_000;
        long remainder = millis % 1_000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", hours, minutes, seconds, remainder);
    }

    private static String fileStem(String fileName) {
        int extension = fileName.lastIndexOf('.');
        return extension > 0 ? fileName.substring(0, extension) : fileName;
    }

    private static String safeLanguage(String language) {
        String normalized = language == null ? "" : language.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-+", "-");
        return normalized.isEmpty() ? "original" : normalized;
    }

    private static void moveWithoutReplacing(Path source, Path destination) throws IOException {
        Files.move(source, destination);
    }
}
