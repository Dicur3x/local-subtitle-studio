package io.github.dicur3x.lss.infrastructure.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DefaultExternalProcessRunner implements ExternalProcessRunner {
    private static final long CANCELLATION_POLL_MILLIS = 100;

    @Override
    public ProcessResult run(List<String> command, BooleanSupplier cancellationRequested)
            throws IOException, InterruptedException {
        return runStreaming(command, cancellationRequested, line -> { }, line -> { });
    }

    @Override
    public ProcessResult runStreaming(
            List<String> command,
            BooleanSupplier cancellationRequested,
            Consumer<String> standardOutputLine,
            Consumer<String> standardErrorLine
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        Objects.requireNonNull(standardOutputLine, "standardOutputLine");
        Objects.requireNonNull(standardErrorLine, "standardErrorLine");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();

        try (var readers = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> standardOutput = readers.submit(
                    () -> readUtf8Lines(process.getInputStream(), standardOutputLine));
            Future<String> standardError = readers.submit(
                    () -> readUtf8Lines(process.getErrorStream(), standardErrorLine));

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

    private static String readUtf8Lines(InputStream inputStream, Consumer<String> lineConsumer)
            throws IOException {
        StringBuilder output = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!output.isEmpty()) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
                lineConsumer.accept(line);
            }
        }
        return output.toString();
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
