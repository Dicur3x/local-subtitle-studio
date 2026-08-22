package io.github.dicur3x.lss.infrastructure.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class DefaultExternalProcessRunner implements ExternalProcessRunner {
    private static final long CANCELLATION_POLL_MILLIS = 100;

    @Override
    public ProcessResult run(List<String> command, BooleanSupplier cancellationRequested)
            throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();

        try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> standardOutput = readers.submit(() -> readUtf8(process.getInputStream()));
            Future<String> standardError = readers.submit(() -> readUtf8(process.getErrorStream()));

            while (!process.waitFor(CANCELLATION_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                if (cancellationRequested.getAsBoolean()) {
                    terminate(process);
                    throw new CancellationException("External process was cancelled");
                }
            }

            return new ProcessResult(
                    process.exitValue(),
                    getReaderResult(standardOutput),
                    getReaderResult(standardError)
            );
        } catch (InterruptedException exception) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw exception;
        } catch (RuntimeException | IOException exception) {
            terminate(process);
            throw exception;
        }
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String getReaderResult(Future<String> reader) throws IOException, InterruptedException {
        try {
            return reader.get();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Could not read external process output", exception.getCause());
        }
    }

    private static void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ignored) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
