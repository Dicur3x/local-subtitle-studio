package io.github.dicur3x.lss.media.model;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;

public record AudioTrack(
        int streamIndex,
        String codec,
        OptionalLong bitrate,
        OptionalInt sampleRate,
        int channels,
        String channelLayout,
        String language,
        String title
) {
    public AudioTrack {
        if (streamIndex < 0) {
            throw new IllegalArgumentException("streamIndex must not be negative");
        }
        if (channels < 0) {
            throw new IllegalArgumentException("channels must not be negative");
        }
        codec = normalize(codec);
        bitrate = Objects.requireNonNull(bitrate, "bitrate");
        sampleRate = Objects.requireNonNull(sampleRate, "sampleRate");
        channelLayout = normalize(channelLayout);
        language = normalize(language);
        title = normalize(title);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
