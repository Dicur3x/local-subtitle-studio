package io.github.dicur3x.lss.translation;

import java.nio.file.Path;
import java.util.Objects;

public record CreatedTranslations(
        Path translatedFile,
        TranslatedSubtitles subtitles
) {
    public CreatedTranslations {
        translatedFile = Objects.requireNonNull(translatedFile, "translatedFile")
                .toAbsolutePath().normalize();
        subtitles = Objects.requireNonNull(subtitles, "subtitles");
    }
}
