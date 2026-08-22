package io.github.dicur3x.lss.components;

import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import io.github.dicur3x.lss.models.InstalledModelBundle;
import io.github.dicur3x.lss.models.WhisperModelManager;
import io.github.dicur3x.lss.models.WhisperModelProfile;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import io.github.dicur3x.lss.settings.SettingsException;
import io.github.dicur3x.lss.settings.SettingsManager;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

public final class ManagedToolsService {
    private static final Pattern FFMPEG_VERSION = Pattern.compile(
            "(?im)^ffmpeg version\\s+([^\\s]+)");
    private static final Pattern WHISPER_VERSION = Pattern.compile(
            "(?im)(?:whisper(?:\\.cpp)?\\s+)?version[:\\s]+v?([0-9]+(?:\\.[0-9]+){1,3})");

    private final Map<ManagedComponent, ComponentReleaseProvider> releaseProviders;
    private final ManagedComponentStore componentStore;
    private final ManagedComponentInstaller componentInstaller;
    private final WhisperModelManager modelManager;
    private final SettingsManager settingsManager;
    private final ExternalProcessRunner processRunner;

    public ManagedToolsService(
            ComponentReleaseProvider ffmpegReleaseProvider,
            ComponentReleaseProvider whisperReleaseProvider,
            ManagedComponentStore componentStore,
            ManagedComponentInstaller componentInstaller,
            WhisperModelManager modelManager,
            SettingsManager settingsManager,
            ExternalProcessRunner processRunner
    ) {
        releaseProviders = new EnumMap<>(ManagedComponent.class);
        releaseProviders.put(ManagedComponent.FFMPEG,
                Objects.requireNonNull(ffmpegReleaseProvider, "ffmpegReleaseProvider"));
        releaseProviders.put(ManagedComponent.WHISPER_CPP,
                Objects.requireNonNull(whisperReleaseProvider, "whisperReleaseProvider"));
        this.componentStore = Objects.requireNonNull(componentStore, "componentStore");
        this.componentInstaller = Objects.requireNonNull(componentInstaller, "componentInstaller");
        this.modelManager = Objects.requireNonNull(modelManager, "modelManager");
        this.settingsManager = Objects.requireNonNull(settingsManager, "settingsManager");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public ComponentCheck check(ManagedComponent component, BooleanSupplier cancellationRequested)
            throws ComponentException {
        ComponentRelease latest = releaseProviders.get(component).latest(cancellationRequested);
        Optional<InstalledComponent> managed = componentStore.current(component);
        String configuredPath = configuredPath(component);
        String configuredVersion = detectVersion(component, configuredPath, cancellationRequested);
        boolean updateAvailable = configuredVersion.isBlank()
                || !normalizeVersion(configuredVersion).equals(normalizeVersion(latest.version()));
        return new ComponentCheck(component, configuredVersion, configuredPath,
                managed, latest, updateAvailable);
    }

    public InstalledComponent install(
            ComponentRelease release,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws ComponentException {
        InstalledComponent installed = componentInstaller.install(release, progress, cancellationRequested);
        try {
            ApplicationSettings current = settingsManager.current();
            ApplicationSettings updated = installed.component() == ManagedComponent.FFMPEG
                    ? current.withManagedFfmpeg(
                    installed.primaryExecutablePath().toString(),
                    installed.secondaryExecutablePath().toString())
                    : current.withManagedWhisper(installed.primaryExecutablePath().toString());
            settingsManager.save(updated);
            return installed;
        } catch (SettingsException exception) {
            throw new ComponentException(installed.component().displayName()
                    + " was installed, but its path could not be saved in application settings.", exception);
        }
    }

    public Optional<InstalledComponent> current(ManagedComponent component) throws ComponentException {
        return componentStore.current(component);
    }

    public Optional<InstalledModelBundle> currentModel() throws ComponentException {
        return modelManager.current();
    }

    public InstalledModelBundle installModel(
            WhisperModelProfile profile,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws ComponentException {
        InstalledModelBundle bundle = modelManager.install(profile, progress, cancellationRequested);
        try {
            settingsManager.save(settingsManager.current().withManagedModels(
                    bundle.modelPath().toString(), bundle.vadModelPath().toString()));
            return bundle;
        } catch (SettingsException exception) {
            throw new ComponentException("The model was installed, but its path could not be saved.", exception);
        }
    }

    private String configuredPath(ManagedComponent component) {
        return component == ManagedComponent.FFMPEG
                ? settingsManager.current().ffmpegExecutable()
                : settingsManager.current().whisperExecutable();
    }

    private String detectVersion(
            ManagedComponent component,
            String executable,
            BooleanSupplier cancellationRequested
    ) {
        if (executable.isBlank()) {
            return "";
        }
        List<String> command = component == ManagedComponent.FFMPEG
                ? List.of(executable, "-version") : List.of(executable, "--version");
        try {
            ProcessResult result = processRunner.run(command, cancellationRequested);
            if (result.exitCode() != 0) {
                return "";
            }
            String output = result.standardOutput() + System.lineSeparator() + result.standardError();
            var matcher = (component == ManagedComponent.FFMPEG ? FFMPEG_VERSION : WHISPER_VERSION)
                    .matcher(output);
            return matcher.find() ? matcher.group(1) : "";
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Version check was interrupted");
        } catch (IOException exception) {
            return "";
        }
    }

    private static String normalizeVersion(String version) {
        String normalized = version.strip().toLowerCase();
        return normalized.startsWith("v") ? normalized.substring(1) : normalized;
    }
}
