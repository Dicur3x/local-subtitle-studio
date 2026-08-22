package io.github.dicur3x.lss.components;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

public interface DownloadClient {
    String getText(URI uri, long maximumBytes, BooleanSupplier cancellationRequested)
            throws IOException, InterruptedException;

    DownloadResult download(
            URI uri,
            Path destination,
            long maximumBytes,
            String phase,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws IOException, InterruptedException;
}
