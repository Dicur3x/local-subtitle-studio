package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.OutputLocation;
import io.github.dicur3x.lss.settings.OutputPreferences;
import io.github.dicur3x.lss.settings.SubtitlePreferences;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SrtWriter {
    private final SubtitleTextFormatter textFormatter;
    private final OutputPreferences outputPreferences;

    public SrtWriter() {
        this(SubtitlePreferences.defaults(), OutputPreferences.defaults());
    }

    public SrtWriter(SubtitlePreferences preferences) {
        this(preferences, OutputPreferences.defaults());
    }

    public SrtWriter(SubtitlePreferences preferences, OutputPreferences outputPreferences) {
        textFormatter = new SubtitleTextFormatter(preferences);
        this.outputPreferences = Objects.requireNonNull(outputPreferences, "outputPreferences");
    }

    public Path write(Path mediaFile, String language, List<SubtitleCue> cues)
            throws SubtitleCreationException {
        Path media = Objects.requireNonNull(mediaFile, "mediaFile").toAbsolutePath().normalize();
        Path directory = outputDirectory(media);
        String stem = fileStem(media.getFileName().toString());
        String languagePart = safeLanguage(language);
        return writeNamed(directory, stem + "." + languagePart, cues);
    }

    public Path writeTranslated(
            Path sourceSubtitle,
            String sourceLanguage,
            String targetLanguage,
            List<SubtitleCue> cues
    ) throws SubtitleCreationException {
        Path source = requiredSubtitle(sourceSubtitle);
        String stem = translationStem(source.getFileName().toString(), sourceLanguage);
        return writeNamed(source.getParent(), stem + "." + safeLanguage(targetLanguage), cues);
    }

    private Path writeNamed(Path directory, String baseName, List<SubtitleCue> cues)
            throws SubtitleCreationException {
        List<SubtitleCue> safeCues = List.copyOf(Objects.requireNonNull(cues, "cues"));
        if (safeCues.isEmpty()) {
            throw new SubtitleCreationException("An empty SRT file was not created.");
        }
        return writeNamedContents(directory, baseName, render(safeCues));
    }

    private Path writeNamedContents(Path directory, String baseName, String contents)
            throws SubtitleCreationException {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, ".lss-subtitles-", ".tmp");
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            for (int copy = 1; copy < 10_000; copy++) {
                String suffix = copy == 1 ? "" : "." + copy;
                Path destination = directory.resolve(baseName + suffix + ".srt");
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
            throw new SubtitleCreationException("Could not save the SRT file in the selected output folder.", exception);
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

    private static Path requiredSubtitle(Path sourceSubtitle) throws SubtitleCreationException {
        Path source = Objects.requireNonNull(sourceSubtitle, "sourceSubtitle")
                .toAbsolutePath().normalize();
        Path directory = source.getParent();
        if (!Files.isRegularFile(source) || directory == null
                || !Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new SubtitleCreationException("The original subtitle file or its folder is not writable.");
        }
        return source;
    }

    private static String translationStem(String fileName, String sourceLanguage) {
        String stem = fileStem(fileName);
        String language = java.util.regex.Pattern.quote(safeLanguage(sourceLanguage));
        return stem.replaceFirst("(?i)\\." + language + "(?:\\.[0-9]+)?$", "");
    }

    public void replace(Path subtitleFile, List<SubtitleCue> cues) throws SubtitleCreationException {
        Path destination = Objects.requireNonNull(subtitleFile, "subtitleFile")
                .toAbsolutePath().normalize();
        Path directory = destination.getParent();
        if (directory == null || !Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new SubtitleCreationException("The subtitle folder is not writable.");
        }
        List<SubtitleCue> safeCues = List.copyOf(Objects.requireNonNull(cues, "cues"));
        if (safeCues.isEmpty()) {
            throw new SubtitleCreationException("An empty subtitle file was not saved.");
        }

        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, ".lss-review-", ".tmp");
            Files.writeString(temporary, render(safeCues), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new SubtitleCreationException("Could not save the reviewed SRT file.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A successfully moved temporary file no longer exists.
                }
            }
        }
    }

    private Path outputDirectory(Path media) throws SubtitleCreationException {
        Path videoDirectory = media.getParent();
        if (videoDirectory == null) {
            throw new SubtitleCreationException("The video has no parent folder for subtitle output.");
        }
        Path directory = switch (outputPreferences.location()) {
            case BESIDE_VIDEO -> videoDirectory;
            case SUBS_FOLDER -> videoDirectory.resolve("Subs");
            case CUSTOM_FOLDER -> customOutputDirectory();
        };
        try {
            if (outputPreferences.location() != OutputLocation.BESIDE_VIDEO) {
                Files.createDirectories(directory);
            }
        } catch (IOException exception) {
            throw new SubtitleCreationException("Could not create the selected subtitle folder.", exception);
        }
        if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new SubtitleCreationException("The selected subtitle folder is not writable.");
        }
        return directory;
    }

    private Path customOutputDirectory() throws SubtitleCreationException {
        if (outputPreferences.customDirectory().isBlank()) {
            throw new SubtitleCreationException("Choose a custom subtitle folder in Advanced settings first.");
        }
        try {
            return Path.of(outputPreferences.customDirectory()).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            throw new SubtitleCreationException("The custom subtitle folder path is invalid.", exception);
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
