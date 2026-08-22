package io.github.dicur3x.lss.media.ffprobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.media.model.AudioTrack;
import io.github.dicur3x.lss.media.model.MediaInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class FfprobeJsonParser {
    private final ObjectMapper objectMapper;

    FfprobeJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    MediaInfo parse(Path mediaFile, String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        Duration duration = parseDuration(root.path("format").path("duration"));
        List<AudioTrack> tracks = new ArrayList<>();

        for (JsonNode stream : root.path("streams")) {
            tracks.add(new AudioTrack(
                    requiredNonNegativeInt(stream, "index"),
                    text(stream, "codec_name"),
                    positiveLong(stream, "bit_rate"),
                    positiveInt(stream, "sample_rate"),
                    nonNegativeInt(stream, "channels"),
                    text(stream, "channel_layout"),
                    text(stream.path("tags"), "language"),
                    text(stream.path("tags"), "title")
            ));
        }

        return new MediaInfo(mediaFile, duration, tracks);
    }

    private static Duration parseDuration(JsonNode durationNode) {
        if (durationNode.isMissingNode() || durationNode.isNull()) {
            return Duration.ZERO;
        }
        try {
            double seconds = Double.parseDouble(durationNode.asText());
            if (!Double.isFinite(seconds) || seconds <= 0) {
                return Duration.ZERO;
            }
            return Duration.ofNanos(Math.round(seconds * 1_000_000_000d));
        } catch (NumberFormatException ignored) {
            return Duration.ZERO;
        }
    }

    private static int requiredNonNegativeInt(JsonNode node, String fieldName) throws IOException {
        JsonNode value = node.path(fieldName);
        if (!value.canConvertToInt() || value.asInt() < 0) {
            throw new IOException("ffprobe returned an invalid " + fieldName);
        }
        return value.asInt();
    }

    private static int nonNegativeInt(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.canConvertToInt() ? Math.max(0, value.asInt()) : 0;
    }

    private static OptionalInt positiveInt(JsonNode node, String fieldName) {
        try {
            int value = Integer.parseInt(node.path(fieldName).asText());
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private static OptionalLong positiveLong(JsonNode node, String fieldName) {
        try {
            long value = Long.parseLong(node.path(fieldName).asText());
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() ? value.asText() : "";
    }
}
