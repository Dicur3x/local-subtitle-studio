package io.github.dicur3x.lss.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsPathsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void explicitDataPathOverridesThePlatformDefault() {
        String previous = System.getProperty("lss.data.path");
        try {
            Path chosen = temporaryDirectory.resolve("chosen-data");
            System.setProperty("lss.data.path", chosen.toString());

            assertEquals(chosen.toAbsolutePath(), SettingsPaths.applicationDataDirectory());
        } finally {
            restore("lss.data.path", previous);
        }
    }

    @Test
    void packagedPortableMarkerSelectsDataBesideTheLauncher() throws Exception {
        String previousData = System.getProperty("lss.data.path");
        String previousApp = System.getProperty("jpackage.app-path");
        try {
            System.clearProperty("lss.data.path");
            Path launcher = Files.write(temporaryDirectory.resolve("Local Subtitle Studio.exe"), new byte[]{1});
            Files.writeString(temporaryDirectory.resolve("portable.mode"), "portable");
            System.setProperty("jpackage.app-path", launcher.toString());

            assertEquals(temporaryDirectory.resolve("data"), SettingsPaths.applicationDataDirectory());
        } finally {
            restore("lss.data.path", previousData);
            restore("jpackage.app-path", previousApp);
        }
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
