package io.github.dicur3x.lss.components;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public record ComponentRelease(
        ManagedComponent component,
        String version,
        URI downloadUri,
        Optional<String> expectedSha256,
        URI releaseNotesUri,
        URI sourceCodeUri,
        String licenseSummary
) {
    public ComponentRelease {
        component = Objects.requireNonNull(component, "component");
        version = requireText(version, "version");
        downloadUri = requireHttps(downloadUri, "downloadUri");
        expectedSha256 = Objects.requireNonNull(expectedSha256, "expectedSha256")
                .map(String::strip).map(String::toLowerCase);
        expectedSha256.ifPresent(value -> {
            if (!value.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("expectedSha256 must contain 64 hexadecimal characters");
            }
        });
        releaseNotesUri = requireHttps(releaseNotesUri, "releaseNotesUri");
        sourceCodeUri = requireHttps(sourceCodeUri, "sourceCodeUri");
        licenseSummary = requireText(licenseSummary, "licenseSummary");
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static URI requireHttps(URI uri, String name) {
        URI value = Objects.requireNonNull(uri, name);
        if (!"https".equalsIgnoreCase(value.getScheme())) {
            throw new IllegalArgumentException(name + " must use HTTPS");
        }
        return value;
    }
}
