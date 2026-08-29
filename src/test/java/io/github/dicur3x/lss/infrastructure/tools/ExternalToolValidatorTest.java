package io.github.dicur3x.lss.infrastructure.tools;

import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalToolValidatorTest {
    @Test
    void treatsWhisperAsOptionalUntilTranscriptionIsEnabled() {
        var validator = new ExternalToolValidator((command, cancelled) ->
                new ProcessResult(0, command.getFirst() + " version 1", ""));
        var settings = new ApplicationSettings(5, "ffmpeg", "ffprobe", "", "", "", "", null, null, null);

        ToolValidationReport report = validator.validate(settings, () -> false);

        assertTrue(report.requiredToolsAvailable());
        assertTrue(report.checks().stream().anyMatch(check -> check.name().equals("whisper.cpp")
                && check.status() == ToolStatus.NOT_CONFIGURED && !check.requiredNow()));
    }

    @Test
    void reportsMissingRequiredExecutableWithoutThrowing() {
        var validator = new ExternalToolValidator((command, cancelled) -> {
            if (command.getFirst().equals("ffprobe")) {
                throw new IOException("not found");
            }
            return new ProcessResult(0, "available", "");
        });
        var settings = new ApplicationSettings(5, "ffmpeg", "ffprobe", "", "", "", "", null, null, null);

        ToolValidationReport report = validator.validate(settings, () -> false);

        assertFalse(report.requiredToolsAvailable());
        assertTrue(report.checks().stream().anyMatch(check -> check.name().equals("FFprobe")
                && check.status() == ToolStatus.ERROR));
    }
}
