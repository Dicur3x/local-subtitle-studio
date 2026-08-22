package io.github.dicur3x.lss.media.ffprobe;

import io.github.dicur3x.lss.infrastructure.process.DefaultExternalProcessRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
class FfprobeMediaProbeIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void probesTwoRealAudioStreams() throws Exception {
        assumeTrue(commandIsAvailable("ffmpeg"), "ffmpeg is not available");
        assumeTrue(commandIsAvailable("ffprobe"), "ffprobe is not available");

        Path mediaFile = tempDirectory.resolve("unicode-пример.mkv");
        List<String> createMedia = List.of(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=0.5",
                "-f", "lavfi", "-i", "sine=frequency=880:duration=0.5",
                "-map", "0:a", "-map", "1:a",
                "-c:a", "pcm_s16le",
                "-metadata:s:a:0", "language=eng",
                "-metadata:s:a:0", "title=Main",
                "-metadata:s:a:1", "language=rus",
                "-metadata:s:a:1", "title=Commentary",
                mediaFile.toString()
        );

        Process ffmpeg = new ProcessBuilder(createMedia).redirectErrorStream(true).start();
        String output = new String(ffmpeg.getInputStream().readAllBytes());
        assertTrue(ffmpeg.waitFor(20, TimeUnit.SECONDS), "ffmpeg timed out");
        assertEquals(0, ffmpeg.exitValue(), output);

        var probe = new FfprobeMediaProbe("ffprobe", new DefaultExternalProcessRunner());
        var media = probe.probe(mediaFile, () -> false);

        assertEquals(2, media.audioTracks().size());
        assertEquals("eng", media.audioTracks().get(0).language());
        assertEquals("rus", media.audioTracks().get(1).language());
        assertEquals("Commentary", media.audioTracks().get(1).title());
        assertTrue(media.duration().compareTo(Duration.ofMillis(400)) >= 0);
    }

    private static boolean commandIsAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
