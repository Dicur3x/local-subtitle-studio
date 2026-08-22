package io.github.dicur3x.lss.infrastructure.process;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;

public interface ExternalProcessRunner {
    ProcessResult run(List<String> command, BooleanSupplier cancellationRequested)
            throws IOException, InterruptedException;
}
