package io.github.dicur3x.lss.components;

import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import io.github.dicur3x.lss.models.InstalledModelBundle;
import io.github.dicur3x.lss.models.InstalledTranslationModel;
import io.github.dicur3x.lss.models.TranslationModelManager;
import io.github.dicur3x.lss.models.TranslationModelProfile;
import io.github.dicur3x.lss.models.WhisperModelManager;
import io.github.dicur3x.lss.models.WhisperModelProfile;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import io.github.dicur3x.lss.settings.SettingsException;
import io.github.dicur3x.lss.settings.SettingsManager;
import io.github.dicur3x.lss.settings.SettingsPaths;

import java.io.IOException;
import java.nio.file.Path;
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
    private static final Pattern LLAMA_BUILD_VERSION = Pattern.compile(
            "(?im)^version:.*?\\bbuild\\s+([0-9]+)\\b");
    private static final Pattern VERSION_NUMBER = Pattern.compile(
            "(?i)(?:^|[^0-9])v?([0-9]+(?:\\.[0-9]+){1,3})(?=$|[^0-9])");

    private final Map<ManagedComponent, ComponentReleaseProvider> releaseProviders;
    private final ManagedComponentStore componentStore;
    private final ManagedComponentInstaller componentInstaller;
    private final WhisperModelManager modelManager;
    private final TranslationModelManager translationModelManager;
    private final SettingsManager settingsManager;
    private final ExternalProcessRunner processRunner;

    public ManagedToolsService(
            ComponentReleaseProvider ffmpegReleaseProvider,
            ComponentReleaseProvider whisperReleaseProvider,
            ComponentReleaseProvider llamaReleaseProvider,
            ManagedComponentStore componentStore,
            ManagedComponentInstaller componentInstaller,
            WhisperModelManager modelManager,
            TranslationModelManager translationModelManager,
            SettingsManager settingsManager,
            ExternalProcessRunner processRunner
    ) {
        releaseProviders = new EnumMap<>(ManagedComponent.class);
        releaseProviders.put(ManagedComponent.FFMPEG,
                Objects.requireNonNull(ffmpegReleaseProvider, "ffmpegReleaseProvider"));
        releaseProviders.put(ManagedComponent.WHISPER_CPP,
                Objects.requireNonNull(whisperReleaseProvider, "whisperReleaseProvider"));
        releaseProviders.put(ManagedComponent.LLAMA_CPP,
                Objects.requireNonNull(llamaReleaseProvider, "llamaReleaseProvider"));
        this.componentStore = Objects.requireNonNull(componentStore, "componentStore");
        this.componentInstaller = Objects.requireNonNull(componentInstaller, "componentInstaller");
        this.modelManager = Objects.requireNonNull(modelManager, "modelManager");
        this.translationModelManager = Objects.requireNonNull(
                translationModelManager, "translationModelManager");
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
            ApplicationSettings updated = switch (installed.component()) {
                case FFMPEG -> current.withManagedFfmpeg(
                        installed.primaryExecutablePath().toString(),
                        installed.secondaryExecutablePath().toString());
                case WHISPER_CPP -> current.withManagedWhisper(
                        installed.primaryExecutablePath().toString());
                case LLAMA_CPP -> current.withManagedLlama(
                        installed.primaryExecutablePath().toString());
            };
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

    public boolean isModelInstalled(WhisperModelProfile profile) {
        return modelManager.isInstalled(profile);
    }

    public Optional<InstalledTranslationModel> currentTranslationModel() throws ComponentException {
        return translationModelManager.current();
    }

    public boolean isTranslationModelInstalled(TranslationModelProfile profile) {
        return translationModelManager.isInstalled(profile);
    }

    public InstalledTranslationModel installTranslationModel(
            TranslationModelProfile profile,
            OperationProgress progress,
            BooleanSupplier cancellationRequested
    ) throws ComponentException {
        InstalledTranslationModel model = translationModelManager.install(
                profile, progress, cancellationRequested);
        try {
            settingsManager.save(settingsManager.current().withManagedTranslationModel(
                    model.modelPath().toString()));
            return model;
        } catch (SettingsException exception) {
            throw new ComponentException(
                    "The translation model was installed, but its path could not be saved.", exception);
        }
    }

    public Path managedStorageDirectory() {
        return SettingsPaths.managedStorageDirectory(settingsManager.current());
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
        return switch (component) {
            case FFMPEG -> settingsManager.current().ffmpegExecutable();
            case WHISPER_CPP -> settingsManager.current().whisperExecutable();
            case LLAMA_CPP -> settingsManager.current().llamaExecutable();
        };
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
            if (component == ManagedComponent.LLAMA_CPP) {
                return llamaBuildVersion(output);
            }
            Pattern versionPattern = switch (component) {
                case FFMPEG -> FFMPEG_VERSION;
                case WHISPER_CPP -> WHISPER_VERSION;
                case LLAMA_CPP -> throw new IllegalStateException("Handled above");
            };
            var matcher = versionPattern.matcher(output);
            if (!matcher.find()) {
                return "";
            }
            return matcher.group(1);
        } catch (CancellationException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Version check was interrupted");
        } catch (IOException exception) {
            return "";
        }
    }

    static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.strip().toLowerCase();
        if (normalized.matches("b[0-9]+")) {
            return normalized.substring(1);
        }
        var matcher = VERSION_NUMBER.matcher(normalized);
        return matcher.find() ? matcher.group(1) : normalized;
    }

    static String llamaBuildVersion(String output) {
        var matcher = LLAMA_BUILD_VERSION.matcher(output == null ? "" : output);
        return matcher.find() ? "b" + matcher.group(1) : "";
    }
}
