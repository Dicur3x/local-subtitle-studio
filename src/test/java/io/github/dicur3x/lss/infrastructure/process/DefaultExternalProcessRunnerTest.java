package io.github.dicur3x.lss.infrastructure.process;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultExternalProcessRunnerTest {
    @Test
    void streamsLinesWhileRetainingProcessOutput() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        List<String> errorLines = new ArrayList<>();

        ProcessResult result = new DefaultExternalProcessRunner().runStreaming(
                List.of(java.toString(), "-version"), () -> false, line -> { }, errorLines::add);

        assertEquals(0, result.exitCode());
        assertFalse(errorLines.isEmpty());
        assertFalse(result.standardError().isBlank());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows");
    }
}
