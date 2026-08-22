package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.media.model.AudioTrack;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioTrackDisplayFormatterTest {
    private final AudioTrackDisplayFormatter formatter = new AudioTrackDisplayFormatter();

    @Test
    void formatsKnownLanguageCodecAndLayout() {
        var track = new AudioTrack(
                1,
                "eac3",
                OptionalLong.of(640_000),
                OptionalInt.of(48_000),
                6,
                "5.1(side)",
                "eng",
                "Commentary"
        );

        assertEquals(
                "1. English — Commentary  ·  E-AC3  ·  5.1(side)  ·  640 kbps  ·  48 kHz",
                formatter.format(track)
        );
    }

    @Test
    void fallsBackWhenMetadataIsMissing() {
        var track = new AudioTrack(
                0,
                "aac",
                OptionalLong.empty(),
                OptionalInt.empty(),
                2,
                "",
                "",
                ""
        );

        assertEquals("0. Unknown language  ·  AAC  ·  Stereo", formatter.format(track));
    }
}
