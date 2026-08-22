package io.github.dicur3x.lss.subtitles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WhisperJsonParser {
    private final ObjectMapper objectMapper;

    public WhisperJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public TranscriptionResult parse(Path jsonFile) throws SubtitleCreationException {
        try {
            return parse(objectMapper.readTree(jsonFile.toFile()));
        } catch (IOException exception) {
            throw new SubtitleCreationException("Could not read whisper.cpp transcription output.", exception);
        }
    }

    TranscriptionResult parse(String json) throws SubtitleCreationException {
        try {
            return parse(objectMapper.readTree(json));
        } catch (IOException exception) {
            throw new SubtitleCreationException("Could not parse whisper.cpp transcription output.", exception);
        }
    }

    private static TranscriptionResult parse(JsonNode root) throws SubtitleCreationException {
        JsonNode transcription = root.path("transcription");
        if (!transcription.isArray()) {
            throw new SubtitleCreationException("whisper.cpp returned an unexpected JSON format.");
        }
        String language = root.path("result").path("language").asText("original");
        List<RecognizedSegment> segments = new ArrayList<>();
        long id = 1;
        for (JsonNode segment : transcription) {
            String text = normalizeText(segment.path("text").asText());
            if (text.isEmpty()) {
                continue;
            }
            long segmentStart = nonNegative(segment.path("offsets").path("from").asLong(-1));
            long segmentEnd = nonNegative(segment.path("offsets").path("to").asLong(-1));
            List<TokenTiming> tokens = parseTokens(segment.path("tokens"));
            long speechStart = tokens.stream().mapToLong(token -> token.start().toMillis()).min()
                    .orElse(segmentStart);
            long speechEnd = tokens.stream().mapToLong(token -> token.end().toMillis()).max()
                    .orElse(segmentEnd);
            if (speechStart < 0 || speechEnd <= speechStart) {
                continue;
            }
            segments.add(new RecognizedSegment(
                    id++, Duration.ofMillis(speechStart), Duration.ofMillis(speechEnd), text, tokens));
        }
        return new TranscriptionResult(language, segments);
    }

    private static List<TokenTiming> parseTokens(JsonNode tokensNode) {
        if (!tokensNode.isArray()) {
            return List.of();
        }
        List<TokenTiming> tokens = new ArrayList<>();
        for (JsonNode token : tokensNode) {
            String text = token.path("text").asText();
            JsonNode offsets = token.path("offsets");
            long start = offsets.path("from").asLong(-1);
            long end = offsets.path("to").asLong(-1);
            if (start < 0 || end <= start || isSpecialToken(text)) {
                continue;
            }
            tokens.add(new TokenTiming(text, Duration.ofMillis(start), Duration.ofMillis(end),
                    token.path("p").asDouble(0)));
        }
        return List.copyOf(tokens);
    }

    private static boolean isSpecialToken(String text) {
        String value = text == null ? "" : text.strip();
        return value.isEmpty()
                || (value.startsWith("[") && value.endsWith("]"))
                || (value.startsWith("<|") && value.endsWith("|>"));
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static long nonNegative(long value) {
        return value < 0 ? -1 : value;
    }
}
