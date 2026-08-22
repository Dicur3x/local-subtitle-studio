package io.github.dicur3x.lss.subtitles;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface SubtitleCreationService {
    CreatedSubtitles create(
            Path mediaFile,
            int audioStreamIndex,
            BooleanSupplier cancellationRequested,
            Consumer<String> progress
    ) throws SubtitleCreationException;
}
