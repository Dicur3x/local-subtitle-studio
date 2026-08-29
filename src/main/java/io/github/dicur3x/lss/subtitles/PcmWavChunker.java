package io.github.dicur3x.lss.subtitles;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Splits the canonical recognition WAV so every Whisper process starts with clean text context. */
final class PcmWavChunker {
    static final Duration DEFAULT_CORE_DURATION = Duration.ofMinutes(8);
    static final Duration DEFAULT_OVERLAP = Duration.ofSeconds(5);

    private final Duration coreDuration;
    private final Duration overlap;

    PcmWavChunker() {
        this(DEFAULT_CORE_DURATION, DEFAULT_OVERLAP);
    }

    PcmWavChunker(Duration coreDuration, Duration overlap) {
        this.coreDuration = requirePositive(coreDuration, "coreDuration");
        this.overlap = Objects.requireNonNull(overlap, "overlap");
        if (overlap.isNegative() || overlap.multipliedBy(2).compareTo(coreDuration) >= 0) {
            throw new IllegalArgumentException("overlap must be non-negative and shorter than half a chunk");
        }
    }

    List<RecognitionAudioChunk> split(Path audioFile, BooleanSupplier cancellationRequested)
            throws SubtitleCreationException {
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Path audio = Objects.requireNonNull(audioFile, "audioFile").toAbsolutePath().normalize();
        try (AudioInputStream input = AudioSystem.getAudioInputStream(audio.toFile())) {
            AudioFormat format = input.getFormat();
            validateFormat(format);
            long totalFrames = input.getFrameLength();
            if (totalFrames <= 0 || totalFrames == AudioSystem.NOT_SPECIFIED) {
                throw new SubtitleCreationException("The prepared WAV has no readable duration.");
            }
            long coreFrames = frames(coreDuration, format.getFrameRate());
            long overlapFrames = frames(overlap, format.getFrameRate());
            Duration totalDuration = duration(totalFrames, format.getFrameRate());
            if (totalFrames <= coreFrames) {
                return List.of(new RecognitionAudioChunk(
                        audio, Duration.ZERO, Duration.ZERO, totalDuration, false));
            }

            List<RecognitionAudioChunk> chunks = new ArrayList<>();
            try {
                for (long coreStart = 0; coreStart < totalFrames; coreStart += coreFrames) {
                    throwIfCancelled(cancellationRequested);
                    long coreEnd = Math.min(totalFrames, coreStart + coreFrames);
                    long actualStart = Math.max(0, coreStart - overlapFrames);
                    long actualEnd = Math.min(totalFrames, coreEnd + overlapFrames);
                    Path chunkFile = writeChunk(
                            audio, format, actualStart, actualEnd, cancellationRequested);
                    chunks.add(new RecognitionAudioChunk(
                            chunkFile,
                            duration(actualStart, format.getFrameRate()),
                            duration(coreStart, format.getFrameRate()),
                            duration(coreEnd, format.getFrameRate()),
                            true));
                }
                return List.copyOf(chunks);
            } catch (IOException | UnsupportedAudioFileException | RuntimeException exception) {
                deleteTemporaryChunks(chunks);
                throw exception;
            }
        } catch (UnsupportedAudioFileException exception) {
            throw new SubtitleCreationException("The prepared audio is not a supported PCM WAV file.", exception);
        } catch (IOException exception) {
            throw new SubtitleCreationException("Could not split the prepared audio for reliable recognition.", exception);
        }
    }

    static void deleteTemporaryChunks(List<RecognitionAudioChunk> chunks) {
        for (RecognitionAudioChunk chunk : chunks) {
            if (chunk.temporary()) {
                try {
                    Files.deleteIfExists(chunk.file());
                } catch (IOException ignored) {
                    // PreparedAudio performs a final cleanup attempt for the enclosing directory.
                }
            }
        }
    }

    private static Path writeChunk(
            Path source,
            AudioFormat format,
            long startFrame,
            long endFrame,
            BooleanSupplier cancellationRequested
    ) throws IOException, UnsupportedAudioFileException {
        int frameSize = format.getFrameSize();
        long frameCount = endFrame - startFrame;
        int byteCount = Math.toIntExact(Math.multiplyExact(frameCount, frameSize));
        byte[] audioBytes;
        try (AudioInputStream input = AudioSystem.getAudioInputStream(source.toFile())) {
            skipFully(input, Math.multiplyExact(startFrame, frameSize), cancellationRequested);
            audioBytes = readFully(input, byteCount, cancellationRequested);
        }

        Path destination = Files.createTempFile(source.getParent(), "whisper-chunk-", ".wav");
        boolean written = false;
        try (var bytes = new ByteArrayInputStream(audioBytes);
             var chunk = new AudioInputStream(bytes, format, frameCount)) {
            if (AudioSystem.write(chunk, AudioFileFormat.Type.WAVE, destination.toFile()) <= 0) {
                throw new IOException("Java Sound did not write the WAV chunk");
            }
            written = true;
            return destination;
        } finally {
            if (!written) {
                Files.deleteIfExists(destination);
            }
        }
    }

    private static void skipFully(
            AudioInputStream input,
            long bytes,
            BooleanSupplier cancellationRequested
    ) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            throwIfCancelled(cancellationRequested);
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else if (input.read() < 0) {
                throw new EOFException("Prepared WAV ended before the requested chunk");
            } else {
                remaining--;
            }
        }
    }

    private static byte[] readFully(
            AudioInputStream input,
            int byteCount,
            BooleanSupplier cancellationRequested
    ) throws IOException {
        byte[] result = new byte[byteCount];
        int offset = 0;
        while (offset < result.length) {
            throwIfCancelled(cancellationRequested);
            int read = input.read(result, offset, result.length - offset);
            if (read < 0) {
                throw new EOFException("Prepared WAV ended inside a recognition chunk");
            }
            offset += read;
        }
        return result;
    }

    private static void validateFormat(AudioFormat format) throws SubtitleCreationException {
        boolean canonical = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && Math.round(format.getSampleRate()) == 16_000
                && format.getChannels() == 1
                && format.getSampleSizeInBits() == 16
                && format.getFrameSize() == 2
                && !format.isBigEndian();
        if (!canonical) {
            throw new SubtitleCreationException(
                    "The prepared audio is not the expected 16 kHz mono PCM WAV.");
        }
    }

    private static long frames(Duration duration, float frameRate) {
        return Math.max(1, Math.round(duration.toNanos() / 1_000_000_000d * frameRate));
    }

    private static Duration duration(long frames, float frameRate) {
        return Duration.ofNanos(Math.round(frames / (double) frameRate * 1_000_000_000d));
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    private static void throwIfCancelled(BooleanSupplier cancellationRequested) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.getAsBoolean()) {
            throw new CancellationException("Audio chunking was cancelled");
        }
    }
}
