package io.github.dicur3x.lss.settings;

import java.nio.file.Path;

public final class SettingsPaths {
    private SettingsPaths() {
    }

    public static Path defaultSettingsFile() {
        return applicationDataDirectory().resolve("settings.json");
    }

    public static Path applicationDataDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "LocalSubtitleStudio");
        }
        return Path.of(System.getProperty("user.home"), ".config", "local-subtitle-studio");
    }
}
