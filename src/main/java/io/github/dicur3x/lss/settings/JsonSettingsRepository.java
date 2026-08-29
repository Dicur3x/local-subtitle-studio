package io.github.dicur3x.lss.settings;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

public final class JsonSettingsRepository implements SettingsRepository {
    private final Path settingsFile;
    private final ObjectMapper objectMapper;

    public JsonSettingsRepository(Path settingsFile, ObjectMapper objectMapper) {
        this.settingsFile = Objects.requireNonNull(settingsFile, "settingsFile").toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public Optional<ApplicationSettings> load() throws SettingsException {
        if (!Files.exists(settingsFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(settingsFile.toFile(), ApplicationSettings.class));
        } catch (IOException exception) {
            throw new SettingsException("Could not read settings from " + settingsFile, exception);
        }
    }

    @Override
    public void save(ApplicationSettings settings) throws SettingsException {
        Path parent = settingsFile.getParent();
        Path temporaryFile = null;
        try {
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, "settings-", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), settings);
            moveIntoPlace(temporaryFile);
            temporaryFile = null;
        } catch (IOException exception) {
            throw new SettingsException("Could not save settings to " + settingsFile, exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // The failed temporary file can be cleaned by the operating system later.
                }
            }
        }
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, settingsFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, settingsFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
