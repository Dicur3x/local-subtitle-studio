package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleSegmenterTest {
    @Test
    void splitsLongTextAtWordTokensAndKeepsTheirSpeechBoundaries() {
        SubtitlePreferences preferences = new SubtitlePreferences(10, 2, 800, 50, 200, 100, 20);
        RecognizedSegment source = new RecognizedSegment(
                1, ms(1_000), ms(2_600), "one two three four five six seven eight",
                List.of(
                        token(" one two", 1_000, 1_400),
                        token(" three four", 1_400, 1_800),
                        token(" five six", 1_800, 2_200),
                        token(" seven eight", 2_200, 2_600)
                ));

        List<RecognizedSegment> result = new SubtitleSegmenter(preferences).segment(List.of(source));

        assertEquals(2, result.size());
        assertEquals("one two three four", result.getFirst().text());
        assertEquals(ms(1_000), result.getFirst().speechStart());
        assertEquals(ms(1_800), result.getFirst().speechEnd());
        assertEquals("five six seven eight", result.getLast().text());
        assertTrue(result.stream().allMatch(segment -> segment.text().length() <= 20));
    }

    private static TokenTiming token(String text, long start, long end) {
        return new TokenTiming(text, ms(start), ms(end), 0.9);
    }

    private static Duration ms(long value) {
        return Duration.ofMillis(value);
    }
}
