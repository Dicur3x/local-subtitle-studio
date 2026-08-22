package io.github.dicur3x.lss.settings;

import java.util.Objects;

public final class SettingsManager {
    private final SettingsRepository repository;
    private volatile ApplicationSettings current;

    public SettingsManager(SettingsRepository repository) throws SettingsException {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.current = repository.load().orElseGet(ApplicationSettings::defaults);
    }

    public SettingsManager(SettingsRepository repository, ApplicationSettings initialSettings) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.current = Objects.requireNonNull(initialSettings, "initialSettings");
    }

    public ApplicationSettings current() {
        return current;
    }

    public synchronized void save(ApplicationSettings settings) throws SettingsException {
        ApplicationSettings normalized = Objects.requireNonNull(settings, "settings");
        repository.save(normalized);
        current = normalized;
    }
}
