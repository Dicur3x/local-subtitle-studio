package io.github.dicur3x.lss.subtitles;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

final class TestAudioFiles {
    private TestAudioFiles() {
    }

    static Path writeCanonicalWav(Path destination, int seconds) throws IOException {
        AudioFormat format = new AudioFormat(16_000, 16, 1, true, false);
        byte[] pcm = new byte[Math.multiplyExact(seconds, 16_000 * 2)];
        try (var bytes = new ByteArrayInputStream(pcm);
             var audio = new AudioInputStream(bytes, format, pcm.length / format.getFrameSize())) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, destination.toFile());
        }
        return destination;
    }
}
