package io.github.dicur3x.lss.settings;

import java.util.Optional;

public interface SettingsRepository {
    Optional<ApplicationSettings> load() throws SettingsException;

    void save(ApplicationSettings settings) throws SettingsException;
}
