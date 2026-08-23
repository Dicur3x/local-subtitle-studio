package io.github.dicur3x.lss.settings;

import java.nio.file.Path;
import java.nio.file.Files;

public final class SettingsPaths {
    private SettingsPaths() {
    }

    public static Path defaultSettingsFile() {
        return applicationDataDirectory().resolve("settings.json");
    }

    public static Path applicationDataDirectory() {
        String configured = System.getProperty("lss.data.path");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("LSS_DATA_PATH");
        }
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String applicationPath = System.getProperty("jpackage.app-path");
        if (applicationPath != null && !applicationPath.isBlank()) {
            Path executableDirectory = Path.of(applicationPath).toAbsolutePath().normalize().getParent();
            if (executableDirectory != null && Files.isRegularFile(executableDirectory.resolve("portable.mode"))) {
                return executableDirectory.resolve("data");
            }
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "LocalSubtitleStudio");
        }
        return Path.of(System.getProperty("user.home"), ".config", "local-subtitle-studio");
    }
}
