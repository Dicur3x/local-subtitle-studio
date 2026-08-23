package io.github.dicur3x.lss;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.audio.AudioExtractor;
import io.github.dicur3x.lss.audio.FfmpegAudioExtractor;
import io.github.dicur3x.lss.components.FfmpegReleaseProvider;
import io.github.dicur3x.lss.components.HttpDownloadClient;
import io.github.dicur3x.lss.components.ManagedComponentInstaller;
import io.github.dicur3x.lss.components.ManagedComponentStore;
import io.github.dicur3x.lss.components.ManagedToolsService;
import io.github.dicur3x.lss.components.WhisperReleaseProvider;
import io.github.dicur3x.lss.infrastructure.process.DefaultExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.tools.ExternalToolValidator;
import io.github.dicur3x.lss.media.MediaProbe;
import io.github.dicur3x.lss.media.ffprobe.FfprobeMediaProbe;
import io.github.dicur3x.lss.models.WhisperModelManager;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import io.github.dicur3x.lss.settings.JsonSettingsRepository;
import io.github.dicur3x.lss.settings.SettingsException;
import io.github.dicur3x.lss.settings.SettingsManager;
import io.github.dicur3x.lss.settings.SettingsPaths;
import io.github.dicur3x.lss.subtitles.SubtitleCreationService;
import io.github.dicur3x.lss.subtitles.WhisperSubtitleCreationService;
import io.github.dicur3x.lss.ui.MainView;
import io.github.dicur3x.lss.ui.ComponentsDialog;
import io.github.dicur3x.lss.ui.SettingsDialog;
import io.github.dicur3x.lss.ui.I18n;
import io.github.dicur3x.lss.ui.FirstRunDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.github.dicur3x.lss.ui.I18n.tr;

public final class LocalSubtitleStudioApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(LocalSubtitleStudioApplication.class.getName());
    private MainView mainView;
    private SettingsManager settingsManager;
    private ExternalProcessRunner processRunner;
    private ManagedToolsService managedToolsService;
    private String startupSettingsWarning;
    private boolean firstRun;
    private boolean openComponentsAfterStart;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) ->
                LOGGER.log(Level.SEVERE, "Unhandled exception on " + thread.getName(), exception));
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        processRunner = new DefaultExternalProcessRunner();
        settingsManager = loadSettings();
        if (firstRun) {
            runFirstSetup();
        }
        I18n.use(settingsManager.current().uiLanguage());
        managedToolsService = createManagedToolsService();
        MediaProbe mediaProbe = (file, cancellationRequested) ->
                new FfprobeMediaProbe(settingsManager.current().ffprobeExecutable(), processRunner)
                        .probe(file, cancellationRequested);
        AudioExtractor audioExtractor = (file, streamIndex, cancellationRequested) ->
                new FfmpegAudioExtractor(
                        settingsManager.current().ffmpegExecutable(),
                        settingsManager.current().temporaryDirectory(),
                        processRunner
                ).extract(file, streamIndex, cancellationRequested);
        SubtitleCreationService subtitleCreationService = new WhisperSubtitleCreationService(
                settingsManager::current, processRunner, new ObjectMapper());
        mainView = new MainView(
                mediaProbe, audioExtractor, subtitleCreationService, this::showComponents, this::showSettings);

        Scene scene = new Scene(mainView.root(), 920, 690);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"),
                "app.css"
        ).toExternalForm());

        stage.setTitle("Local Subtitle Studio");
        stage.setMinWidth(760);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
        if (openComponentsAfterStart) {
            Platform.runLater(() -> showComponents(stage));
        }
        if (startupSettingsWarning != null) {
            Platform.runLater(() -> showAlert(stage, Alert.AlertType.WARNING,
                    tr("app.settingsLoadFailed"), startupSettingsWarning));
        }
        inspectInitialFileArgument();
    }

    private SettingsManager loadSettings() {
        Path settingsFile = SettingsPaths.defaultSettingsFile();
        firstRun = !Files.exists(settingsFile);
        var repository = new JsonSettingsRepository(settingsFile, new ObjectMapper());
        try {
            return new SettingsManager(repository);
        } catch (SettingsException exception) {
            LOGGER.log(Level.WARNING, "Could not load application settings; defaults will be used", exception);
            startupSettingsWarning = "Default paths will be used for this session. Open Settings and save "
                    + "the correct tool paths. The existing settings file was not overwritten.";
            return new SettingsManager(repository, ApplicationSettings.defaults());
        }
    }

    private void runFirstSetup() {
        new FirstRunDialog().showAndWait().ifPresent(result -> {
            try {
                settingsManager.save(settingsManager.current().withOnboarding(
                        result.uiLanguage(), result.outputPreferences()));
                openComponentsAfterStart = result.openComponents();
            } catch (SettingsException exception) {
                LOGGER.log(Level.WARNING, "Could not save first-run settings", exception);
                startupSettingsWarning = exception.getMessage();
            }
        });
    }

    private ManagedToolsService createManagedToolsService() {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpDownloadClient downloadClient = new HttpDownloadClient();
        Path applicationData = SettingsPaths.applicationDataDirectory();
        ManagedComponentStore store = new ManagedComponentStore(
                applicationData.resolve("components"), objectMapper);
        return new ManagedToolsService(
                new FfmpegReleaseProvider(downloadClient),
                new WhisperReleaseProvider(downloadClient, objectMapper),
                store,
                new ManagedComponentInstaller(downloadClient, store),
                new WhisperModelManager(applicationData.resolve("models"), downloadClient, objectMapper),
                settingsManager,
                processRunner
        );
    }

    private void showComponents(Window owner) {
        new ComponentsDialog(managedToolsService, url -> getHostServices().showDocument(url))
                .showAndWait(owner);
        if (mainView != null) {
            mainView.refreshReadiness();
        }
    }

    private void showSettings(Window owner) {
        SettingsDialog dialog = new SettingsDialog(new ExternalToolValidator(processRunner));
        var previousLanguage = settingsManager.current().uiLanguage();
        dialog.showAndWait(owner, settingsManager.current()).ifPresent(settings -> {
            try {
                settingsManager.save(settings);
                if (settings.uiLanguage() != previousLanguage) {
                    showAlert(owner, Alert.AlertType.INFORMATION,
                            tr("app.languageSaved"), tr("app.restartForLanguage"));
                }
            } catch (SettingsException exception) {
                LOGGER.log(Level.SEVERE, "Could not save application settings", exception);
                showAlert(owner, Alert.AlertType.ERROR, tr("app.settingsNotSaved"), exception.getMessage());
            }
        });
    }

    private static void showAlert(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle("Local Subtitle Studio");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void inspectInitialFileArgument() {
        getParameters().getUnnamed().stream().findFirst().ifPresent(argument -> {
            try {
                mainView.inspect(Path.of(argument));
            } catch (InvalidPathException exception) {
                LOGGER.log(Level.WARNING, "Ignoring invalid media path from startup arguments", exception);
            }
        });
    }

    @Override
    public void stop() {
        if (mainView != null) {
            mainView.close();
        }
    }
}
