package io.github.dicur3x.lss.media.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record MediaInfo(Path file, Duration duration, List<AudioTrack> audioTracks) {
    public MediaInfo {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        duration = Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        audioTracks = List.copyOf(Objects.requireNonNull(audioTracks, "audioTracks"));
    }
}
