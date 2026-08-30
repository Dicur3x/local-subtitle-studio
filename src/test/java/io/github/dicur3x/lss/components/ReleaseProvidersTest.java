package io.github.dicur3x.lss.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseProvidersTest {
    @Test
    void ffmpegUsesPublishedVersionAndChecksum() throws Exception {
        String checksum = "a".repeat(64);
        var client = new MetadataClient(Map.of(
                FfmpegReleaseProvider.VERSION_URI, "9.0.1\n",
                FfmpegReleaseProvider.CHECKSUM_URI, checksum + "  ffmpeg-release-essentials.zip\n",
                URI.create("https://raw.githubusercontent.com/FFmpeg/FFmpeg/n9.0.1/Changelog"),
                "version 9.0.1:\n fix one\n fix two\n\nversion 9.0:\n- new feature\n"
        ));

        ComponentRelease release = new FfmpegReleaseProvider(client).latest(() -> false);

        assertEquals(ManagedComponent.FFMPEG, release.component());
        assertEquals("9.0.1", release.version());
        assertEquals(checksum, release.expectedSha256().orElseThrow());
        assertTrue(release.releaseNotes().contains("fix two"));
        assertTrue(!release.releaseNotes().contains("new feature"));
        assertTrue(release.sourceCodeUri().toString().endsWith("ffmpeg-9.0.1.tar.xz"));
    }

    @Test
    void whisperSkipsBuildsAndPrereleasesAndReadsGithubDigest() throws Exception {
        String checksum = "b".repeat(64);
        String json = """
                [
                  {"tag_name":"b4938","draft":false,"prerelease":false,"assets":[]},
                  {"tag_name":"v1.9.3","draft":false,"prerelease":true,"assets":[]},
                  {"tag_name":"v1.9.2","draft":false,"prerelease":false,
                   "html_url":"https://github.com/ggml-org/whisper.cpp/releases/tag/v1.9.2",
                   "body":"## What's Changed\\n* Fix VAD timestamps by @author",
                   "assets":[{"name":"whisper-bin-x64.zip",
                     "browser_download_url":"https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.2/whisper-bin-x64.zip",
                     "digest":"sha256:%s"}]}
                ]
                """.formatted(checksum);
        var client = new MetadataClient(Map.of(WhisperReleaseProvider.RELEASES_URI, json));

        ComponentRelease release = new WhisperReleaseProvider(client, new ObjectMapper())
                .latest(() -> false);

        assertEquals(ManagedComponent.WHISPER_CPP, release.component());
        assertEquals("1.9.2", release.version());
        assertEquals(checksum, release.expectedSha256().orElseThrow());
        assertTrue(release.releaseNotes().contains("Fix VAD timestamps"));
    }

    @Test
    void llamaResolvesStableReleaseToItsChecksummedWindowsBuild() throws Exception {
        String checksum = "c".repeat(64);
        URI buildTag = URI.create(
                "https://github.com/ggml-org/llama.cpp/releases/download/v0.3.0/nightly-tag.txt");
        URI buildMetadata = URI.create(
                "https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/b10621");
        String stable = """
                {"tag_name":"v0.3.0","draft":false,"prerelease":false,
                 "html_url":"https://github.com/ggml-org/llama.cpp/releases/tag/v0.3.0",
                 "body":"Stable fixes",
                 "assets":[{"name":"nightly-tag.txt","browser_download_url":"%s"}]}
                """.formatted(buildTag);
        String build = """
                {"tag_name":"b10621","assets":[
                  {"name":"llama-b10621-bin-win-cpu-x64.zip",
                   "browser_download_url":"https://github.com/ggml-org/llama.cpp/releases/download/b10621/llama-b10621-bin-win-cpu-x64.zip",
                   "digest":"sha256:%s"}]}
                """.formatted(checksum);
        var client = new MetadataClient(Map.of(
                LlamaReleaseProvider.LATEST_RELEASE_URI, stable,
                buildTag, "b10621\n",
                buildMetadata, build));

        ComponentRelease release = new LlamaReleaseProvider(client, new ObjectMapper())
                .latest(() -> false);

        assertEquals(ManagedComponent.LLAMA_CPP, release.component());
        assertEquals("b10621", release.version());
        assertEquals(checksum, release.expectedSha256().orElseThrow());
        assertTrue(release.releaseNotes().contains("v0.3.0"));
        assertTrue(release.sourceCodeUri().toString().endsWith("v0.3.0.tar.gz"));
    }

    private static final class MetadataClient implements DownloadClient {
        private final Map<URI, String> responses = new HashMap<>();

        private MetadataClient(Map<URI, String> responses) {
            this.responses.putAll(responses);
        }

        @Override
        public String getText(URI uri, long maximumBytes, BooleanSupplier cancellationRequested)
                throws IOException {
            String response = responses.get(uri);
            if (response == null) {
                throw new IOException("Unexpected URI: " + uri);
            }
            return response;
        }

        @Override
        public DownloadResult download(
                URI uri, Path destination, long maximumBytes, String phase,
                OperationProgress progress, BooleanSupplier cancellationRequested
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
