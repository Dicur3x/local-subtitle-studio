package io.github.dicur3x.lss.subtitles;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface SubtitleCreationService {
    default SubtitleReadiness readiness() {
        return SubtitleReadiness.readyState();
    }

    CreatedSubtitles create(
            Path mediaFile,
            int audioStreamIndex,
            String spokenLanguage,
            BooleanSupplier cancellationRequested,
            Consumer<PipelineProgress> progress
    ) throws SubtitleCreationException;

    default CreatedSubtitles create(
            Path mediaFile,
            int audioStreamIndex,
            BooleanSupplier cancellationRequested,
            Consumer<PipelineProgress> progress
    ) throws SubtitleCreationException {
        return create(mediaFile, audioStreamIndex, SpokenLanguage.AUTO.code(), cancellationRequested, progress);
    }
}
