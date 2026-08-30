package io.github.dicur3x.lss.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledTranslationModel(
        int schemaVersion,
        String profileId,
        String modelName,
        String modelFile,
        String modelSha256,
        String sourceUri,
        String licenseSummary,
        String installedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public InstalledTranslationModel {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        profileId = normalize(profileId);
        modelName = normalize(modelName);
        modelFile = normalize(modelFile);
        modelSha256 = normalize(modelSha256).toLowerCase();
        sourceUri = normalize(sourceUri);
        licenseSummary = normalize(licenseSummary);
        installedAt = normalize(installedAt);
    }

    public TranslationModelProfile profile() {
        return TranslationModelProfile.fromId(profileId);
    }

    public Path modelPath() {
        return Path.of(modelFile).toAbsolutePath().normalize();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
