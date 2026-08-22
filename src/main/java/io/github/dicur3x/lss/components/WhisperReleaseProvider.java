package io.github.dicur3x.lss.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class WhisperReleaseProvider implements ComponentReleaseProvider {
    static final URI RELEASES_URI = URI.create(
            "https://api.github.com/repos/ggml-org/whisper.cpp/releases?per_page=20");
    private static final String WINDOWS_ASSET = "whisper-bin-x64.zip";
    private static final long METADATA_LIMIT = 2 * 1024 * 1024;

    private final DownloadClient downloadClient;
    private final ObjectMapper objectMapper;

    public WhisperReleaseProvider(DownloadClient downloadClient, ObjectMapper objectMapper) {
        this.downloadClient = java.util.Objects.requireNonNull(downloadClient, "downloadClient");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public ComponentRelease latest(BooleanSupplier cancellationRequested) throws ComponentException {
        try {
            String json = downloadClient.getText(RELEASES_URI, METADATA_LIMIT, cancellationRequested);
            JsonNode releases = objectMapper.readTree(json);
            if (!releases.isArray()) {
                throw new ComponentException("GitHub returned an invalid whisper.cpp release list.");
            }
            for (JsonNode release : releases) {
                String tag = release.path("tag_name").asText();
                if (release.path("draft").asBoolean() || release.path("prerelease").asBoolean()
                        || !tag.matches("v[0-9]+(?:\\.[0-9]+){1,3}")) {
                    continue;
                }
                Optional<URI> asset = findAsset(release);
                if (asset.isPresent()) {
                    Optional<String> checksum = findAssetChecksum(release);
                    return new ComponentRelease(
                            ManagedComponent.WHISPER_CPP,
                            tag.substring(1),
                            asset.get(),
                            checksum,
                            URI.create(release.path("html_url").asText()),
                            URI.create("https://github.com/ggml-org/whisper.cpp/archive/refs/tags/" + tag + ".tar.gz"),
                            "MIT License"
                    );
                }
            }
            throw new ComponentException("No stable whisper.cpp Windows x64 release was found.");
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("whisper.cpp update check was interrupted");
        } catch (IOException | IllegalArgumentException exception) {
            throw new ComponentException("Could not check the latest whisper.cpp version: "
                    + exception.getMessage(), exception);
        }
    }

    private static Optional<URI> findAsset(JsonNode release) {
        for (JsonNode asset : release.path("assets")) {
            if (WINDOWS_ASSET.equals(asset.path("name").asText())) {
                String url = asset.path("browser_download_url").asText();
                if (!url.isBlank()) {
                    return Optional.of(URI.create(url));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findAssetChecksum(JsonNode release) {
        for (JsonNode asset : release.path("assets")) {
            if (WINDOWS_ASSET.equals(asset.path("name").asText())) {
                String digest = asset.path("digest").asText();
                if (digest.matches("(?i)sha256:[0-9a-f]{64}")) {
                    return Optional.of(digest.substring("sha256:".length()).toLowerCase());
                }
            }
        }
        return Optional.empty();
    }
}
