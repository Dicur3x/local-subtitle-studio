package io.github.dicur3x.lss.subtitles;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhisperJsonParserTest {
    @Test
    void usesRealTokenBoundariesAndIgnoresSpecialTokens() throws Exception {
        String json = """
                {
                  "result": {"language": "ru"},
                  "transcription": [{
                    "timestamps": {"from": "00:00:01,000", "to": "00:00:04,000"},
                    "offsets": {"from": 1000, "to": 4000},
                    "text": "  Привет,   мир! ",
                    "tokens": [
                      {"text": "[_BEG_]", "offsets": {"from": 0, "to": 0}, "p": 1.0},
                      {"text": " Привет", "offsets": {"from": 1120, "to": 1510}, "p": 0.95},
                      {"text": ", мир!", "offsets": {"from": 1510, "to": 1890}, "p": 0.91}
                    ]
                  }]
                }
                """;

        TranscriptionResult result = new WhisperJsonParser(new ObjectMapper()).parse(json);

        assertEquals("ru", result.language());
        assertEquals(1, result.segments().size());
        RecognizedSegment segment = result.segments().getFirst();
        assertEquals("Привет, мир!", segment.text());
        assertEquals(Duration.ofMillis(1120), segment.speechStart());
        assertEquals(Duration.ofMillis(1890), segment.speechEnd());
        assertEquals(2, segment.tokens().size());
    }
}
