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
                FfmpegReleaseProvider.CHECKSUM_URI, checksum + "  ffmpeg-release-essentials.zip\n"
        ));

        ComponentRelease release = new FfmpegReleaseProvider(client).latest(() -> false);

        assertEquals(ManagedComponent.FFMPEG, release.component());
        assertEquals("9.0.1", release.version());
        assertEquals(checksum, release.expectedSha256().orElseThrow());
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
