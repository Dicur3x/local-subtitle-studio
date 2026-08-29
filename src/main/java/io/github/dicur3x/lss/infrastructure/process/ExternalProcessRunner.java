package io.github.dicur3x.lss.infrastructure.process;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface ExternalProcessRunner {
    ProcessResult run(List<String> command, BooleanSupplier cancellationRequested)
            throws IOException, InterruptedException;

    default ProcessResult runStreaming(
            List<String> command,
            BooleanSupplier cancellationRequested,
            Consumer<String> standardOutputLine,
            Consumer<String> standardErrorLine
    ) throws IOException, InterruptedException {
        ProcessResult result = run(command, cancellationRequested);
        result.standardOutput().lines().forEach(standardOutputLine);
        result.standardError().lines().forEach(standardErrorLine);
        return result;
    }
}
