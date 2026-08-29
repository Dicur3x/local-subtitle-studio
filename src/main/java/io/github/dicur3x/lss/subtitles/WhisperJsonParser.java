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
            List<TokenTiming> tokens = reconcileTokenTimeline(
                    parseTokens(segment.path("tokens")), segmentStart, segmentEnd);
            long speechStart = segmentStart;
            long speechEnd = segmentEnd;
            if (speechStart < 0 || speechEnd <= speechStart) {
                speechStart = tokens.stream().mapToLong(token -> token.start().toMillis()).min().orElse(-1);
                speechEnd = tokens.stream().mapToLong(token -> token.end().toMillis()).max().orElse(-1);
            }
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

    /**
     * whisper.cpp 1.9.2 maps segment offsets back to the original audio after VAD removes silence,
     * while full-JSON token offsets can still use the compressed VAD timeline. Detect that mismatch
     * and project token positions into the authoritative segment interval.
     */
    private static List<TokenTiming> reconcileTokenTimeline(
            List<TokenTiming> tokens,
            long segmentStart,
            long segmentEnd
    ) {
        if (tokens.isEmpty() || segmentStart < 0 || segmentEnd <= segmentStart) {
            return tokens;
        }
        long tokenStart = tokens.stream().mapToLong(token -> token.start().toMillis()).min().orElse(-1);
        long tokenEnd = tokens.stream().mapToLong(token -> token.end().toMillis()).max().orElse(-1);
        if (tokenStart < 0 || tokenEnd <= tokenStart) {
            return tokens;
        }
        boolean compressedVadTimeline = tokenStart < segmentStart - 100 || tokenEnd > segmentEnd + 100;
        if (!compressedVadTimeline) {
            return tokens;
        }

        long tokenSpan = tokenEnd - tokenStart;
        long segmentSpan = segmentEnd - segmentStart;
        List<TokenTiming> mapped = new ArrayList<>(tokens.size());
        for (TokenTiming token : tokens) {
            long mappedStart = project(
                    token.start().toMillis(), tokenStart, tokenSpan, segmentStart, segmentSpan);
            long mappedEnd = project(
                    token.end().toMillis(), tokenStart, tokenSpan, segmentStart, segmentSpan);
            mappedEnd = Math.max(mappedStart, mappedEnd);
            mapped.add(new TokenTiming(
                    token.text(), Duration.ofMillis(mappedStart), Duration.ofMillis(mappedEnd),
                    token.probability()));
        }
        return List.copyOf(mapped);
    }

    private static long project(
            long value,
            long sourceStart,
            long sourceSpan,
            long destinationStart,
            long destinationSpan
    ) {
        double ratio = (value - sourceStart) / (double) sourceSpan;
        return destinationStart + Math.round(Math.max(0d, Math.min(1d, ratio)) * destinationSpan);
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
