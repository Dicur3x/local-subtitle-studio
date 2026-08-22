package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.infrastructure.tools.ExternalToolValidator;
import io.github.dicur3x.lss.infrastructure.tools.ToolCheck;
import io.github.dicur3x.lss.infrastructure.tools.ToolStatus;
import io.github.dicur3x.lss.infrastructure.tools.ToolValidationReport;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

public final class SettingsDialog {
    private static final ButtonType SAVE = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);

    private final ExternalToolValidator toolValidator;

    public SettingsDialog(ExternalToolValidator toolValidator) {
        this.toolValidator = toolValidator;
    }

    public Optional<ApplicationSettings> showAndWait(Window owner, ApplicationSettings current) {
        Dialog<ApplicationSettings> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Local Subtitle Studio advanced settings");
        dialog.setHeaderText("Manual paths and local storage");
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().addAll(SAVE, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(820);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"),
                "app.css"
        ).toExternalForm());
        dialog.getDialogPane().getStyleClass().add("settings-dialog");

        TextField ffmpeg = field(current.ffmpegExecutable(), "ffmpeg or full path to ffmpeg.exe");
        TextField ffprobe = field(current.ffprobeExecutable(), "ffprobe or full path to ffprobe.exe");
        TextField whisper = field(current.whisperExecutable(), "whisper-cli or full path to whisper-cli.exe");
        TextField model = field(current.whisperModel(), "Not required until transcription is enabled");
        TextField vadModel = field(current.whisperVadModel(), "Installed automatically with a managed model");
        TextField temporaryDirectory = field(current.temporaryDirectory(), "Blank means the system temp directory");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        addExecutableRow(grid, 0, "FFmpeg", ffmpeg, dialog, "Choose ffmpeg executable");
        addExecutableRow(grid, 1, "FFprobe", ffprobe, dialog, "Choose ffprobe executable");
        addExecutableRow(grid, 2, "whisper.cpp CLI", whisper, dialog, "Choose whisper-cli executable");
        addFileRow(grid, 3, "Whisper model", model, dialog, "Choose Whisper model", "Whisper model", "*.bin");
        addFileRow(grid, 4, "VAD model", vadModel, dialog, "Choose VAD model", "VAD model", "*.bin");
        addDirectoryRow(grid, 5, "Temporary files", temporaryDirectory, dialog);

        Label explanation = new Label(
                "Managed components apply their paths automatically. Use this screen only to override them with "
                        + "your own files or change the temporary folder. The original video is never modified."
        );
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted");

        Label validationResult = new Label("Paths have not been checked yet.");
        validationResult.setWrapText(true);
        validationResult.setMinHeight(112);
        validationResult.getStyleClass().add("validation-result");
        Button validate = new Button("Check tools");
        validate.getStyleClass().add("quiet-button");
        AtomicReference<Thread> validationThread = new AtomicReference<>();
        validate.setOnAction(event -> {
            Thread previous = validationThread.getAndSet(null);
            if (previous != null) {
                previous.interrupt();
            }
            validate.setDisable(true);
            validationResult.setText("Checking tools…");
            ApplicationSettings candidate = values(ffmpeg, ffprobe, whisper, model, vadModel, temporaryDirectory);
            Thread thread = Thread.startVirtualThread(() -> {
                try {
                    ToolValidationReport report = toolValidator.validate(candidate,
                            Thread.currentThread()::isInterrupted);
                    Platform.runLater(() -> {
                        validationResult.setText(formatReport(report));
                        validationResult.getStyleClass().removeAll("validation-success", "validation-warning");
                        validationResult.getStyleClass().add(
                                report.requiredToolsAvailable() ? "validation-success" : "validation-warning");
                        validate.setDisable(false);
                    });
                } catch (CancellationException ignored) {
                    Platform.runLater(() -> validate.setDisable(false));
                }
            });
            validationThread.set(thread);
        });

        HBox validationHeader = new HBox(12, validate);
        validationHeader.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(16, explanation, grid, validationHeader, validationResult);
        content.setPadding(new Insets(8, 4, 4, 4));
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> button == SAVE
                ? values(ffmpeg, ffprobe, whisper, model, vadModel, temporaryDirectory)
                : null);
        dialog.setOnHidden(event -> {
            Thread thread = validationThread.get();
            if (thread != null) {
                thread.interrupt();
            }
        });
        return dialog.showAndWait();
    }

    private static TextField field(String value, String prompt) {
        TextField field = new TextField(value);
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private static void addExecutableRow(
            GridPane grid,
            int row,
            String label,
            TextField field,
            Dialog<?> dialog,
            String title
    ) {
        addFileRow(grid, row, label, field, dialog, title, "Executable", "*.exe");
    }

    private static void addFileRow(
            GridPane grid,
            int row,
            String label,
            TextField field,
            Dialog<?> dialog,
            String title,
            String filterName,
            String filterPattern
    ) {
        Button browse = new Button("Browse…");
        browse.getStyleClass().add("quiet-button");
        browse.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().setAll(
                    new FileChooser.ExtensionFilter(filterName, filterPattern),
                    new FileChooser.ExtensionFilter("All files", "*.*")
            );
            setInitialDirectory(chooser, field.getText());
            File selected = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (selected != null) {
                field.setText(selected.getAbsolutePath());
            }
        });
        HBox value = new HBox(8, field, browse);
        HBox.setHgrow(field, Priority.ALWAYS);
        grid.add(new Label(label), 0, row);
        grid.add(value, 1, row);
    }

    private static void addDirectoryRow(
            GridPane grid,
            int row,
            String label,
            TextField field,
            Dialog<?> dialog
    ) {
        Button browse = new Button("Browse…");
        browse.getStyleClass().add("quiet-button");
        browse.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose temporary files directory");
            setInitialDirectory(chooser, field.getText());
            File selected = chooser.showDialog(dialog.getDialogPane().getScene().getWindow());
            if (selected != null) {
                field.setText(selected.getAbsolutePath());
            }
        });
        HBox value = new HBox(8, field, browse);
        HBox.setHgrow(field, Priority.ALWAYS);
        grid.add(new Label(label), 0, row);
        grid.add(value, 1, row);
    }

    private static void setInitialDirectory(FileChooser chooser, String value) {
        pathFrom(value).map(path -> path.toFile().isDirectory() ? path : path.getParent())
                .map(Path::toFile).filter(File::isDirectory).ifPresent(chooser::setInitialDirectory);
    }

    private static void setInitialDirectory(DirectoryChooser chooser, String value) {
        pathFrom(value).map(Path::toFile).filter(File::isDirectory).ifPresent(chooser::setInitialDirectory);
    }

    private static Optional<Path> pathFrom(String value) {
        try {
            return value == null || value.isBlank() ? Optional.empty() : Optional.of(Path.of(value));
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private static ApplicationSettings values(
            TextField ffmpeg,
            TextField ffprobe,
            TextField whisper,
            TextField model,
            TextField vadModel,
            TextField temporaryDirectory
    ) {
        return new ApplicationSettings(
                ApplicationSettings.CURRENT_SCHEMA_VERSION,
                ffmpeg.getText(),
                ffprobe.getText(),
                whisper.getText(),
                model.getText(),
                vadModel.getText(),
                temporaryDirectory.getText()
        );
    }

    private static String formatReport(ToolValidationReport report) {
        StringBuilder text = new StringBuilder();
        for (ToolCheck check : report.checks()) {
            if (text.length() > 0) {
                text.append(System.lineSeparator());
            }
            text.append(symbol(check.status())).append(' ').append(check.name()).append(": ")
                    .append(check.details());
        }
        return text.toString();
    }

    private static String symbol(ToolStatus status) {
        return switch (status) {
            case AVAILABLE -> "✓";
            case NOT_CONFIGURED -> "—";
            case ERROR -> "!";
        };
    }
}
