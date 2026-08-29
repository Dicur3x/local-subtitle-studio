package io.github.dicur3x.lss.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplicationSettings(
        int schemaVersion,
        String ffmpegExecutable,
        String ffprobeExecutable,
        String whisperExecutable,
        String whisperModel,
        String whisperVadModel,
        String temporaryDirectory,
        String managedStorageDirectory,
        SubtitlePreferences subtitlePreferences,
        OutputPreferences outputPreferences,
        UiLanguage uiLanguage
) {
    public static final int CURRENT_SCHEMA_VERSION = 7;

    public ApplicationSettings {
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        ffmpegExecutable = valueOrDefault(ffmpegExecutable, "ffmpeg");
        ffprobeExecutable = valueOrDefault(ffprobeExecutable, "ffprobe");
        whisperExecutable = normalize(whisperExecutable);
        whisperModel = normalize(whisperModel);
        whisperVadModel = normalize(whisperVadModel);
        temporaryDirectory = normalize(temporaryDirectory);
        managedStorageDirectory = normalize(managedStorageDirectory);
        subtitlePreferences = subtitlePreferences == null ? SubtitlePreferences.defaults() : subtitlePreferences;
        outputPreferences = outputPreferences == null ? OutputPreferences.defaults() : outputPreferences;
        uiLanguage = uiLanguage == null ? UiLanguage.ENGLISH : uiLanguage;
    }

    public ApplicationSettings(
            int schemaVersion,
            String ffmpegExecutable,
            String ffprobeExecutable,
            String whisperExecutable,
            String whisperModel,
            String whisperVadModel,
            String temporaryDirectory,
            SubtitlePreferences subtitlePreferences,
            OutputPreferences outputPreferences,
            UiLanguage uiLanguage
    ) {
        this(schemaVersion, ffmpegExecutable, ffprobeExecutable, whisperExecutable,
                whisperModel, whisperVadModel, temporaryDirectory, "",
                subtitlePreferences, outputPreferences, uiLanguage);
    }

    public static ApplicationSettings defaults() {
        return new ApplicationSettings(
                CURRENT_SCHEMA_VERSION,
                configuredValue("lss.ffmpeg.path", "LSS_FFMPEG_PATH", "ffmpeg"),
                configuredValue("lss.ffprobe.path", "LSS_FFPROBE_PATH", "ffprobe"),
                configuredValue("lss.whisper.path", "LSS_WHISPER_PATH", ""),
                configuredValue("lss.whisper.model", "LSS_WHISPER_MODEL", ""),
                configuredValue("lss.whisper.vad.model", "LSS_WHISPER_VAD_MODEL", ""),
                configuredValue("lss.temp.path", "LSS_TEMP_PATH", ""),
                configuredValue("lss.storage.path", "LSS_STORAGE_PATH", ""),
                SubtitlePreferences.defaults(),
                OutputPreferences.defaults(),
                UiLanguage.ENGLISH
        );
    }

    public ApplicationSettings withManagedFfmpeg(String ffmpeg, String ffprobe) {
        return new ApplicationSettings(CURRENT_SCHEMA_VERSION, ffmpeg, ffprobe, whisperExecutable,
                whisperModel, whisperVadModel, temporaryDirectory, managedStorageDirectory,
                subtitlePreferences, outputPreferences, uiLanguage);
    }

    public ApplicationSettings withManagedWhisper(String whisper) {
        return new ApplicationSettings(CURRENT_SCHEMA_VERSION, ffmpegExecutable, ffprobeExecutable, whisper,
                whisperModel, whisperVadModel, temporaryDirectory, managedStorageDirectory,
                subtitlePreferences, outputPreferences, uiLanguage);
    }

    public ApplicationSettings withManagedModels(String model, String vadModel) {
        return new ApplicationSettings(CURRENT_SCHEMA_VERSION, ffmpegExecutable, ffprobeExecutable,
                whisperExecutable, model, vadModel, temporaryDirectory, managedStorageDirectory,
                subtitlePreferences, outputPreferences, uiLanguage);
    }

    public ApplicationSettings withOnboarding(UiLanguage language, OutputPreferences output) {
        return new ApplicationSettings(CURRENT_SCHEMA_VERSION, ffmpegExecutable, ffprobeExecutable,
                whisperExecutable, whisperModel, whisperVadModel, temporaryDirectory, managedStorageDirectory,
                subtitlePreferences, output, language);
    }

    public ApplicationSettings withManagedStorageDirectory(String directory) {
        return new ApplicationSettings(CURRENT_SCHEMA_VERSION, ffmpegExecutable, ffprobeExecutable,
                whisperExecutable, whisperModel, whisperVadModel, temporaryDirectory, directory,
                subtitlePreferences, outputPreferences, uiLanguage);
    }

    private static String configuredValue(String property, String environmentVariable, String fallback) {
        String propertyValue = System.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.strip();
        }
        String environmentValue = System.getenv(environmentVariable);
        return environmentValue == null || environmentValue.isBlank() ? fallback : environmentValue.strip();
    }

    private static String valueOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
