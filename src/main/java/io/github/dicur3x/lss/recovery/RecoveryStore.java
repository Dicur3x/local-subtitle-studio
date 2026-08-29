package io.github.dicur3x.lss.recovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.subtitles.DialogueAudioMode;
import io.github.dicur3x.lss.subtitles.RecognitionChunkKey;
import io.github.dicur3x.lss.subtitles.RecognizedSegment;
import io.github.dicur3x.lss.subtitles.SubtitleCreationException;
import io.github.dicur3x.lss.subtitles.TokenTiming;
import io.github.dicur3x.lss.subtitles.TranscriptionCheckpointStore;
import io.github.dicur3x.lss.subtitles.TranscriptionResult;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Owns the single unfinished subtitle-creation workspace. */
public final class RecoveryStore {
    private static final String MANIFEST = "active-job.json";
    private static final String AUDIO = "recognition-audio.wav";

    private final Supplier<Path> storageDirectory;
    private final ObjectMapper objectMapper;

    public RecoveryStore(Supplier<Path> storageDirectory, ObjectMapper objectMapper) {
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Optional<RecoveryJob> activeJob() {
        Path manifest = workspace().resolve(MANIFEST);
        if (!Files.isRegularFile(manifest)) {
            return Optional.empty();
        }
        try {
            RecoveryJob job = objectMapper.readValue(manifest.toFile(), RecoveryJob.class);
            return job.schemaVersion() == RecoveryJob.CURRENT_SCHEMA_VERSION
                    && !job.mediaFile().isBlank() ? Optional.of(job) : Optional.empty();
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public boolean sourceIsUnchanged(RecoveryJob job) {
        try {
            Path media = job.mediaPath();
            return Files.isRegularFile(media)
                    && Files.size(media) == job.mediaSize()
                    && Files.getLastModifiedTime(media).toMillis() == job.mediaLastModifiedMillis();
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public RecoverySession open(
            Path mediaFile,
            int audioStreamIndex,
            String spokenLanguage,
            DialogueAudioMode audioMode,
            String recognitionProfile
    ) throws SubtitleCreationException {
        Path media = Objects.requireNonNull(mediaFile, "mediaFile").toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspace().resolve("chunks"));
            long size = Files.size(media);
            long modified = Files.getLastModifiedTime(media).toMillis();
            Optional<RecoveryJob> current = activeJob();
            boolean compatible = current.filter(job -> job.matchesSelection(
                            media, audioStreamIndex, spokenLanguage, audioMode))
                    .filter(job -> job.mediaSize() == size && job.mediaLastModifiedMillis() == modified)
                    .isPresent();
            if (!compatible) {
                discard();
                Files.createDirectories(workspace().resolve("chunks"));
            }

            RecoveryJob previous = compatible ? current.orElseThrow() : null;
            boolean profileChanged = previous != null
                    && !previous.recognitionProfile().equals(recognitionProfile);
            if (profileChanged) {
                clearChunks();
            }
            RecoveryJob job = new RecoveryJob(
                    RecoveryJob.CURRENT_SCHEMA_VERSION,
                    media.toString(), size, modified, audioStreamIndex, spokenLanguage, audioMode,
                    recognitionProfile, System.currentTimeMillis(),
                    profileChanged || previous == null ? 0 : previous.completedChunks());
            writeManifest(job);
            return new RecoverySession(this, job);
        } catch (IOException exception) {
            throw new SubtitleCreationException(
                    "Could not create the recovery workspace. Check the component storage folder.", exception);
        }
    }

    public void discard() throws IOException {
        Path directory = workspace();
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    Path audioFile() {
        return workspace().resolve(AUDIO);
    }

    Optional<TranscriptionResult> loadChunk(RecoveryJob job, RecognitionChunkKey key)
            throws SubtitleCreationException {
        Path file = chunkFile(key.index());
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            ChunkData data = objectMapper.readValue(file.toFile(), ChunkData.class);
            if (!data.matches(key)) {
                return Optional.empty();
            }
            return Optional.of(data.toResult());
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    void saveChunk(RecoveryJob job, RecognitionChunkKey key, TranscriptionResult result)
            throws SubtitleCreationException {
        try {
            writeAtomically(chunkFile(key.index()), ChunkData.from(key, result));
            RecoveryJob updated = new RecoveryJob(
                    job.schemaVersion(), job.mediaFile(), job.mediaSize(), job.mediaLastModifiedMillis(),
                    job.audioStreamIndex(), job.spokenLanguage(), job.audioMode(), job.recognitionProfile(),
                    System.currentTimeMillis(), Math.max(job.completedChunks(), key.index() + 1));
            writeManifest(updated);
        } catch (IOException exception) {
            throw new SubtitleCreationException("Could not save a recognition recovery point.", exception);
        }
    }

    private void clearChunks() throws IOException {
        Path chunks = workspace().resolve("chunks");
        if (Files.exists(chunks)) {
            try (var paths = Files.walk(chunks)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        Files.createDirectories(chunks);
    }

    private Path chunkFile(int index) {
        return workspace().resolve("chunks").resolve("chunk-%05d.json".formatted(index));
    }

    private Path workspace() {
        return storageDirectory.get().toAbsolutePath().normalize().resolve("recovery").resolve("active");
    }

    private void writeManifest(RecoveryJob job) throws IOException {
        writeAtomically(workspace().resolve(MANIFEST), job);
    }

    private void writeAtomically(Path destination, Object value) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static final class RecoverySession implements TranscriptionCheckpointStore {
        private final RecoveryStore store;
        private final RecoveryJob job;

        private RecoverySession(RecoveryStore store, RecoveryJob job) {
            this.store = store;
            this.job = job;
        }

        public Path audioFile() {
            return store.audioFile();
        }

        public boolean hasPreparedAudio() {
            try {
                return Files.isRegularFile(audioFile()) && Files.size(audioFile()) > 44;
            } catch (IOException exception) {
                return false;
            }
        }

        @Override
        public Optional<TranscriptionResult> load(RecognitionChunkKey chunk)
                throws SubtitleCreationException {
            return store.loadChunk(job, chunk);
        }

        @Override
        public void save(RecognitionChunkKey chunk, TranscriptionResult result)
                throws SubtitleCreationException {
            store.saveChunk(job, chunk, result);
        }

        public void complete() {
            try {
                store.discard();
            } catch (IOException ignored) {
                // A stale completed workspace is harmless and can be replaced by the next job.
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChunkData(
            int index,
            long offsetNanos,
            long keepFromNanos,
            long keepToNanos,
            String language,
            List<SegmentData> segments
    ) {
        private ChunkData {
            language = Objects.requireNonNullElse(language, "original");
            segments = List.copyOf(Objects.requireNonNullElse(segments, List.of()));
        }

        static ChunkData from(RecognitionChunkKey key, TranscriptionResult result) {
            return new ChunkData(key.index(), key.offset().toNanos(), key.keepFrom().toNanos(),
                    key.keepTo().toNanos(), result.language(),
                    result.segments().stream().map(SegmentData::from).toList());
        }

        boolean matches(RecognitionChunkKey key) {
            return index == key.index()
                    && offsetNanos == key.offset().toNanos()
                    && keepFromNanos == key.keepFrom().toNanos()
                    && keepToNanos == key.keepTo().toNanos();
        }

        TranscriptionResult toResult() {
            return new TranscriptionResult(language, segments.stream().map(SegmentData::toSegment).toList());
        }
    }

    private record SegmentData(
            long id,
            long startNanos,
            long endNanos,
            String text,
            List<TokenData> tokens
    ) {
        private SegmentData {
            tokens = List.copyOf(Objects.requireNonNullElse(tokens, List.of()));
        }

        static SegmentData from(RecognizedSegment segment) {
            return new SegmentData(segment.id(), segment.speechStart().toNanos(),
                    segment.speechEnd().toNanos(), segment.text(),
                    segment.tokens().stream().map(TokenData::from).toList());
        }

        RecognizedSegment toSegment() {
            return new RecognizedSegment(id, Duration.ofNanos(startNanos), Duration.ofNanos(endNanos),
                    text, tokens.stream().map(TokenData::toToken).toList());
        }
    }

    private record TokenData(
            String text,
            long startNanos,
            long endNanos,
            double probability
    ) {
        static TokenData from(TokenTiming token) {
            return new TokenData(token.text(), token.start().toNanos(), token.end().toNanos(),
                    token.probability());
        }

        TokenTiming toToken() {
            return new TokenTiming(text, Duration.ofNanos(startNanos), Duration.ofNanos(endNanos), probability);
        }
    }
}
