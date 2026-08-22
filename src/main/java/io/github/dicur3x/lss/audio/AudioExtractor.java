package io.github.dicur3x.lss.audio;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

public interface AudioExtractor {
    PreparedAudio extract(Path mediaFile, int streamIndex, BooleanSupplier cancellationRequested)
            throws AudioExtractionException;
}
