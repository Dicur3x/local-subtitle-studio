package io.github.dicur3x.lss.media;

import io.github.dicur3x.lss.media.model.MediaInfo;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

public interface MediaProbe {
    MediaInfo probe(Path mediaFile, BooleanSupplier cancellationRequested) throws MediaProbeException;
}
