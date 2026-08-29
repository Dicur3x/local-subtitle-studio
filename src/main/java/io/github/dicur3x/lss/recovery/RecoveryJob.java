package io.github.dicur3x.lss.recovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.dicur3x.lss.subtitles.DialogueAudioMode;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecoveryJob(
        int schemaVersion,
        String mediaFile,
        long mediaSize,
        long mediaLastModifiedMillis,
        int audioStreamIndex,
        String spokenLanguage,
        DialogueAudioMode audioMode,
        String recognitionProfile,
        long updatedAtMillis,
        int completedChunks
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public RecoveryJob {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        mediaFile = Objects.requireNonNullElse(mediaFile, "").strip();
        spokenLanguage = Objects.requireNonNullElse(spokenLanguage, "auto")
                .strip().toLowerCase(Locale.ROOT);
        audioMode = audioMode == null ? DialogueAudioMode.STANDARD : audioMode;
        recognitionProfile = Objects.requireNonNullElse(recognitionProfile, "").strip();
        completedChunks = Math.max(0, completedChunks);
    }

    public Path mediaPath() {
        try {
            return Path.of(mediaFile).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            return Path.of(".").toAbsolutePath().normalize().resolve("missing-media");
        }
    }

    public String displayName() {
        Path name = mediaPath().getFileName();
        return name == null ? mediaFile : name.toString();
    }

    public boolean matchesMedia(Path candidate) {
        return candidate != null && mediaPath().equals(candidate.toAbsolutePath().normalize());
    }

    public boolean matchesSelection(
            Path candidate,
            int streamIndex,
            String language,
            DialogueAudioMode mode
    ) {
        return matchesMedia(candidate)
                && audioStreamIndex == streamIndex
                && spokenLanguage.equals(Objects.requireNonNullElse(language, "auto")
                .strip().toLowerCase(Locale.ROOT))
                && audioMode == mode;
    }
}
