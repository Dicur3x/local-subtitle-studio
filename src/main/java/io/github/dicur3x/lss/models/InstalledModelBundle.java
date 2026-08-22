package io.github.dicur3x.lss.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledModelBundle(
        int schemaVersion,
        String profileId,
        String modelName,
        String modelFile,
        String modelSha256,
        String vadModelFile,
        String vadModelSha256,
        String installedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public InstalledModelBundle {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        profileId = normalize(profileId);
        modelName = normalize(modelName);
        modelFile = normalize(modelFile);
        modelSha256 = normalize(modelSha256).toLowerCase();
        vadModelFile = normalize(vadModelFile);
        vadModelSha256 = normalize(vadModelSha256).toLowerCase();
        installedAt = normalize(installedAt);
    }

    public WhisperModelProfile profile() {
        return WhisperModelProfile.fromId(profileId);
    }

    public Path modelPath() {
        return Path.of(modelFile).toAbsolutePath().normalize();
    }

    public Path vadModelPath() {
        return Path.of(vadModelFile).toAbsolutePath().normalize();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
