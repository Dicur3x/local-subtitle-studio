package io.github.dicur3x.lss.components;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Resolves the official stable llama.cpp release to its checksummed Windows CPU build. */
public final class LlamaReleaseProvider implements ComponentReleaseProvider {
    static final URI LATEST_RELEASE_URI = URI.create(
            "https://api.github.com/repos/ggml-org/llama.cpp/releases/latest");
    private static final String RELEASE_BY_TAG =
            "https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/";
    private static final long METADATA_LIMIT = 4L * 1024 * 1024;
    private static final long TAG_LIMIT = 128;

    private final DownloadClient downloadClient;
    private final ObjectMapper objectMapper;

    public LlamaReleaseProvider(DownloadClient downloadClient, ObjectMapper objectMapper) {
        this.downloadClient = java.util.Objects.requireNonNull(downloadClient, "downloadClient");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public ComponentRelease latest(BooleanSupplier cancellationRequested) throws ComponentException {
        try {
            JsonNode stable = objectMapper.readTree(downloadClient.getText(
                    LATEST_RELEASE_URI, METADATA_LIMIT, cancellationRequested));
            String stableTag = stable.path("tag_name").asText();
            if (stable.path("draft").asBoolean() || stable.path("prerelease").asBoolean()
                    || !stableTag.matches("v[0-9]+(?:\\.[0-9]+){1,3}")) {
                throw new ComponentException("GitHub returned an invalid stable llama.cpp release.");
            }

            URI nightlyTagUri = assetUri(stable, "nightly-tag.txt")
                    .orElseThrow(() -> new ComponentException(
                            "The stable llama.cpp release does not identify its Windows build."));
            String buildTag = firstLine(downloadClient.getText(
                    nightlyTagUri, TAG_LIMIT, cancellationRequested));
            if (!buildTag.matches("b[0-9]+")) {
                throw new ComponentException("llama.cpp returned an invalid Windows build tag.");
            }

            JsonNode build = objectMapper.readTree(downloadClient.getText(
                    URI.create(RELEASE_BY_TAG + buildTag), METADATA_LIMIT, cancellationRequested));
            String assetName = "llama-" + buildTag + "-bin-win-cpu-x64.zip";
            JsonNode asset = findAsset(build, assetName)
                    .orElseThrow(() -> new ComponentException(
                            "No official llama.cpp Windows x64 CPU archive was found."));
            String digest = asset.path("digest").asText();
            if (!digest.matches("(?i)sha256:[0-9a-f]{64}")) {
                throw new ComponentException(
                        "The official llama.cpp Windows archive has no SHA-256 digest.");
            }
            String downloadUrl = asset.path("browser_download_url").asText();
            String stableUrl = stable.path("html_url").asText();
            String notes = "llama.cpp " + stableTag + " · Windows CPU build " + buildTag + "\n\n"
                    + stable.path("body").asText().strip();
            return new ComponentRelease(
                    ManagedComponent.LLAMA_CPP,
                    buildTag,
                    URI.create(downloadUrl),
                    Optional.of(digest.substring("sha256:".length()).toLowerCase()),
                    URI.create(stableUrl),
                    notes,
                    URI.create("https://github.com/ggml-org/llama.cpp/archive/refs/tags/"
                            + stableTag + ".tar.gz"),
                    "MIT License"
            );
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("llama.cpp update check was interrupted");
        } catch (IOException | IllegalArgumentException exception) {
            throw new ComponentException("Could not check the latest llama.cpp version: "
                    + exception.getMessage(), exception);
        }
    }

    private static Optional<URI> assetUri(JsonNode release, String name) {
        return findAsset(release, name).map(asset -> URI.create(
                asset.path("browser_download_url").asText()));
    }

    private static Optional<JsonNode> findAsset(JsonNode release, String name) {
        for (JsonNode asset : release.path("assets")) {
            if (name.equals(asset.path("name").asText())
                    && !asset.path("browser_download_url").asText().isBlank()) {
                return Optional.of(asset);
            }
        }
        return Optional.empty();
    }

    private static String firstLine(String value) throws ComponentException {
        return value.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst()
                .orElseThrow(() -> new ComponentException(
                        "The llama.cpp build tag response was empty."));
    }
}
