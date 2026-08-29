package io.github.dicur3x.lss.components;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledComponent(
        int schemaVersion,
        String componentId,
        String version,
        String primaryExecutable,
        String secondaryExecutable,
        String archiveSha256,
        String downloadUri,
        String releaseNotesUri,
        String sourceCodeUri,
        String licenseSummary,
        String installedAt
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public InstalledComponent {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        componentId = requireText(componentId, "componentId");
        version = requireText(version, "version");
        primaryExecutable = requireText(primaryExecutable, "primaryExecutable");
        secondaryExecutable = normalize(secondaryExecutable);
        archiveSha256 = requireText(archiveSha256, "archiveSha256").toLowerCase();
        downloadUri = requireText(downloadUri, "downloadUri");
        releaseNotesUri = requireText(releaseNotesUri, "releaseNotesUri");
        sourceCodeUri = requireText(sourceCodeUri, "sourceCodeUri");
        licenseSummary = requireText(licenseSummary, "licenseSummary");
        installedAt = requireText(installedAt, "installedAt");
    }

    public ManagedComponent component() {
        for (ManagedComponent candidate : ManagedComponent.values()) {
            if (candidate.id().equals(componentId)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unknown managed component: " + componentId);
    }

    public Path primaryExecutablePath() {
        return Path.of(primaryExecutable).toAbsolutePath().normalize();
    }

    public Path secondaryExecutablePath() {
        if (secondaryExecutable.isBlank()) {
            throw new IllegalStateException("This component has no secondary executable");
        }
        return Path.of(secondaryExecutable).toAbsolutePath().normalize();
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(Objects.requireNonNull(value, name));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
