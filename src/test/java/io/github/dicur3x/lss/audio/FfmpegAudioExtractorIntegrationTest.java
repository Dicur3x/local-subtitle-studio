package io.github.dicur3x.lss.audio;

import io.github.dicur3x.lss.infrastructure.process.DefaultExternalProcessRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class FfmpegAudioExtractorIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void extractsSelectedStreamAsWhisperCompatiblePcmAndCleansItUp() throws Exception {
        assumeTrue(commandIsAvailable("ffmpeg"), "ffmpeg is not available");

        Path mediaFile = tempDirectory.resolve("фильм с дорожками.mkv");
        List<String> createMedia = List.of(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo:d=0.5",
                "-f", "lavfi", "-i", "sine=frequency=880:sample_rate=48000:duration=0.5",
                "-map", "0:a", "-map", "1:a",
                "-c:a", "pcm_s16le",
                mediaFile.toString()
        );
        Process ffmpeg = new ProcessBuilder(createMedia).redirectErrorStream(true).start();
        String output = new String(ffmpeg.getInputStream().readAllBytes());
        assertTrue(ffmpeg.waitFor(20, TimeUnit.SECONDS), "ffmpeg timed out");
        assertEquals(0, ffmpeg.exitValue(), output);

        var extractor = new FfmpegAudioExtractor(
                "ffmpeg", tempDirectory.toString(), new DefaultExternalProcessRunner());
        PreparedAudio prepared = extractor.extract(mediaFile, 1, () -> false);
        Path wavFile = prepared.file();
        Path workingDirectory = wavFile.getParent();

        assertTrue(Files.isRegularFile(wavFile));
        try (var audio = AudioSystem.getAudioInputStream(wavFile.toFile())) {
            AudioFormat format = audio.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
            assertEquals(PreparedAudio.SAMPLE_RATE, Math.round(format.getSampleRate()));
            assertEquals(PreparedAudio.CHANNELS, format.getChannels());
            assertEquals(PreparedAudio.BITS_PER_SAMPLE, format.getSampleSizeInBits());
            byte[] samples = audio.readNBytes(4_000);
            assertTrue(containsNonZeroSample(samples), "the selected sine-wave stream was not extracted");
        }

        prepared.close();
        assertFalse(Files.exists(wavFile));
        assertFalse(Files.exists(workingDirectory));
    }

    private static boolean containsNonZeroSample(byte[] samples) {
        for (byte sample : samples) {
            if (sample != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean commandIsAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
