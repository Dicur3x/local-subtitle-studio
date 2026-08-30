package io.github.dicur3x.lss.infrastructure.tools;

import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import io.github.dicur3x.lss.settings.ApplicationSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class ExternalToolValidator {
    private final ExternalProcessRunner processRunner;

    public ExternalToolValidator(ExternalProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public ToolValidationReport validate(
            ApplicationSettings settings,
            BooleanSupplier cancellationRequested
    ) {
        List<ToolCheck> checks = new ArrayList<>();
        checks.add(checkExecutable("FFmpeg", settings.ffmpegExecutable(), List.of("-version"), true,
                cancellationRequested));
        checks.add(checkExecutable("FFprobe", settings.ffprobeExecutable(), List.of("-version"), true,
                cancellationRequested));
        checks.add(checkExecutable("whisper.cpp", settings.whisperExecutable(), List.of("-h"), false,
                cancellationRequested));
        checks.add(checkModel("Whisper model", settings.whisperModel(),
                "Not needed until transcription is enabled"));
        checks.add(checkModel("VAD model", settings.whisperVadModel(),
                "Installed automatically with a managed model"));
        checks.add(checkExecutable("llama.cpp", settings.llamaExecutable(), List.of("--version"), false,
                cancellationRequested));
        checks.add(checkModel("Translation model", settings.translationModel(),
                "Not needed until subtitle translation is enabled"));
        checks.add(checkTemporaryDirectory(settings.temporaryDirectory()));
        return new ToolValidationReport(checks);
    }

    private ToolCheck checkExecutable(
            String name,
            String executable,
            List<String> arguments,
            boolean requiredNow,
            BooleanSupplier cancellationRequested
    ) {
        if (executable.isBlank()) {
            return new ToolCheck(name, ToolStatus.NOT_CONFIGURED, requiredNow, "Path is not configured");
        }

        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(executable);
        command.addAll(arguments);
        try {
            ProcessResult result = processRunner.run(command, cancellationRequested);
            if (result.exitCode() != 0) {
                return new ToolCheck(name, ToolStatus.ERROR, requiredNow,
                        "Exited with code " + result.exitCode());
            }
            String version = firstNonBlankLine(result.standardOutput(), result.standardError());
            return new ToolCheck(name, ToolStatus.AVAILABLE, requiredNow,
                    "whisper.cpp".equals(name) || version.isBlank() ? "Available" : conciseVersion(version));
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Tool validation was interrupted");
        } catch (IOException exception) {
            return new ToolCheck(name, ToolStatus.ERROR, requiredNow, "Not found or cannot be started");
        }
    }

    private static ToolCheck checkModel(String name, String modelPath, String notConfiguredMessage) {
        if (modelPath.isBlank()) {
            return new ToolCheck(name, ToolStatus.NOT_CONFIGURED, false, notConfiguredMessage);
        }
        try {
            Path path = Path.of(modelPath).toAbsolutePath().normalize();
            if (Files.isRegularFile(path) && Files.isReadable(path)) {
                return new ToolCheck(name, ToolStatus.AVAILABLE, false,
                        path.getFileName().toString());
            }
            return new ToolCheck(name, ToolStatus.ERROR, false, "File cannot be read");
        } catch (InvalidPathException exception) {
            return new ToolCheck(name, ToolStatus.ERROR, false, "Path is invalid");
        }
    }

    private static ToolCheck checkTemporaryDirectory(String directoryPath) {
        if (directoryPath.isBlank()) {
            return new ToolCheck("Temporary directory", ToolStatus.AVAILABLE, true,
                    "System temporary directory");
        }
        try {
            Path path = Path.of(directoryPath).toAbsolutePath().normalize();
            if (Files.isDirectory(path) && Files.isWritable(path)) {
                return new ToolCheck("Temporary directory", ToolStatus.AVAILABLE, true, path.toString());
            }
            return new ToolCheck("Temporary directory", ToolStatus.ERROR, true,
                    "Directory does not exist or is not writable");
        } catch (InvalidPathException exception) {
            return new ToolCheck("Temporary directory", ToolStatus.ERROR, true, "Path is invalid");
        }
    }

    private static String firstNonBlankLine(String... outputs) {
        for (String output : outputs) {
            String line = output.lines().map(String::strip).filter(value -> !value.isEmpty())
                    .findFirst().orElse("");
            if (!line.isEmpty()) {
                return line;
            }
        }
        return "";
    }

    private static String conciseVersion(String versionLine) {
        int copyright = versionLine.indexOf(" Copyright");
        String concise = copyright > 0 ? versionLine.substring(0, copyright) : versionLine;
        return concise.length() <= 100 ? concise : concise.substring(0, 97) + "…";
    }
}
