package io.github.dicur3x.lss.media.ffprobe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfprobeJsonParserTest {
    private final FfprobeJsonParser parser = new FfprobeJsonParser(new ObjectMapper());

    @Test
    void parsesDurationAndAudioTrackMetadata() throws Exception {
        String json = """
                {
                  "streams": [
                    {
                      "index": 2,
                      "codec_name": "eac3",
                      "sample_rate": "48000",
                      "channels": 6,
                      "channel_layout": "5.1(side)",
                      "bit_rate": "640000",
                      "tags": {
                        "language": "eng",
                        "title": "Main audio"
                      }
                    },
                    {
                      "index": 3,
                      "codec_name": "aac",
                      "sample_rate": "48000",
                      "channels": 2,
                      "tags": {
                        "language": "rus"
                      }
                    }
                  ],
                  "format": {
                    "duration": "7542.125000"
                  }
                }
                """;

        var media = parser.parse(Path.of("Movie.mkv"), json);

        assertEquals(Duration.ofSeconds(7542).plusMillis(125), media.duration());
        assertEquals(2, media.audioTracks().size());
        var english = media.audioTracks().getFirst();
        assertEquals(2, english.streamIndex());
        assertEquals("eac3", english.codec());
        assertEquals(640_000L, english.bitrate().orElseThrow());
        assertEquals(48_000, english.sampleRate().orElseThrow());
        assertEquals(6, english.channels());
        assertEquals("5.1(side)", english.channelLayout());
        assertEquals("eng", english.language());
        assertEquals("Main audio", english.title());
    }

    @Test
    void toleratesMissingOptionalValuesAndUnknownDuration() throws Exception {
        String json = """
                {
                  "streams": [{"index": 1, "codec_name": "opus"}],
                  "format": {"duration": "N/A"}
                }
                """;

        var media = parser.parse(Path.of("live.webm"), json);
        var track = media.audioTracks().getFirst();

        assertEquals(Duration.ZERO, media.duration());
        assertFalse(track.bitrate().isPresent());
        assertFalse(track.sampleRate().isPresent());
        assertTrue(track.language().isEmpty());
        assertTrue(track.title().isEmpty());
    }
}
