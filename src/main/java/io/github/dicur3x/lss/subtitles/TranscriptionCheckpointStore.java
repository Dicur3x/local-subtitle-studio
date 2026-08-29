package io.github.dicur3x.lss.subtitles;

import java.util.Optional;

/** Persists completed Whisper chunks so a long recognition job can resume after interruption. */
public interface TranscriptionCheckpointStore {
    Optional<TranscriptionResult> load(RecognitionChunkKey chunk) throws SubtitleCreationException;

    void save(RecognitionChunkKey chunk, TranscriptionResult result) throws SubtitleCreationException;

    static TranscriptionCheckpointStore disabled() {
        return Disabled.INSTANCE;
    }

    enum Disabled implements TranscriptionCheckpointStore {
        INSTANCE;

        @Override
        public Optional<TranscriptionResult> load(RecognitionChunkKey chunk) {
            return Optional.empty();
        }

        @Override
        public void save(RecognitionChunkKey chunk, TranscriptionResult result) {
            // Deliberately not persisted.
        }
    }
}
