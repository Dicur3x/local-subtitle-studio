package io.github.dicur3x.lss;

import io.github.dicur3x.lss.infrastructure.process.DefaultExternalProcessRunner;
import io.github.dicur3x.lss.media.ffprobe.FfprobeExecutableLocator;
import io.github.dicur3x.lss.media.ffprobe.FfprobeMediaProbe;
import io.github.dicur3x.lss.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LocalSubtitleStudioApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(LocalSubtitleStudioApplication.class.getName());
    private MainView mainView;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) ->
                LOGGER.log(Level.SEVERE, "Unhandled exception on " + thread.getName(), exception));
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        var mediaProbe = new FfprobeMediaProbe(
                FfprobeExecutableLocator.locate(),
                new DefaultExternalProcessRunner()
        );
        mainView = new MainView(mediaProbe);

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
        inspectInitialFileArgument();
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
