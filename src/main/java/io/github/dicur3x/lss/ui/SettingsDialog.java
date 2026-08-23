package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.infrastructure.tools.ExternalToolValidator;
import io.github.dicur3x.lss.infrastructure.tools.ToolCheck;
import io.github.dicur3x.lss.infrastructure.tools.ToolStatus;
import io.github.dicur3x.lss.infrastructure.tools.ToolValidationReport;
import io.github.dicur3x.lss.settings.ApplicationSettings;
import io.github.dicur3x.lss.settings.OutputLocation;
import io.github.dicur3x.lss.settings.OutputPreferences;
import io.github.dicur3x.lss.settings.SubtitlePreferences;
import io.github.dicur3x.lss.settings.UiLanguage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.dicur3x.lss.ui.I18n.tr;

public final class SettingsDialog {
    private static final ButtonType SAVE = new ButtonType(tr("common.save"), ButtonBar.ButtonData.OK_DONE);

    private final ExternalToolValidator toolValidator;

    public SettingsDialog(ExternalToolValidator toolValidator) {
        this.toolValidator = toolValidator;
    }

    public Optional<ApplicationSettings> showAndWait(Window owner, ApplicationSettings current) {
        Dialog<ApplicationSettings> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(tr("settings.title"));
        dialog.setHeaderText(tr("settings.header"));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().addAll(SAVE, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(820);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"),
                "app.css"
        ).toExternalForm());
        dialog.getDialogPane().getStyleClass().add("settings-dialog");

        TextField ffmpeg = field(current.ffmpegExecutable(), tr("settings.ffmpegPrompt"));
        TextField ffprobe = field(current.ffprobeExecutable(), tr("settings.ffprobePrompt"));
        TextField whisper = field(current.whisperExecutable(), tr("settings.whisperPrompt"));
        TextField model = field(current.whisperModel(), tr("settings.modelPrompt"));
        TextField vadModel = field(current.whisperVadModel(), tr("settings.vadPrompt"));
        TextField temporaryDirectory = field(current.temporaryDirectory(), tr("settings.tempPrompt"));
        ComboBox<UiLanguage> uiLanguage = new ComboBox<>();
        uiLanguage.getItems().setAll(UiLanguage.values());
        uiLanguage.getSelectionModel().select(current.uiLanguage());
        uiLanguage.setMaxWidth(Double.MAX_VALUE);
        ComboBox<OutputLocation> outputLocation = new ComboBox<>();
        outputLocation.getItems().setAll(OutputLocation.values());
        outputLocation.setConverter(new StringConverter<>() {
            @Override
            public String toString(OutputLocation value) {
                return value == null ? "" : tr("output." + value.name().toLowerCase(java.util.Locale.ROOT));
            }

            @Override
            public OutputLocation fromString(String value) {
                return outputLocation.getValue();
            }
        });
        outputLocation.getSelectionModel().select(current.outputPreferences().location());
        outputLocation.setMaxWidth(Double.MAX_VALUE);
        TextField outputDirectory = field(current.outputPreferences().customDirectory(),
                tr("settings.outputPrompt"));
        SubtitlePreferences currentSubtitles = current.subtitlePreferences();
        Spinner<Integer> charactersPerLine = integerSpinner(
                10, 100, currentSubtitles.maximumCharactersPerLine(), 1);
        Spinner<Integer> maximumLines = integerSpinner(
                1, 4, currentSubtitles.maximumLines(), 1);
        Spinner<Integer> minimumDuration = integerSpinner(
                200, 10_000, currentSubtitles.minimumDurationMs(), 100);
        Spinner<Integer> startPadding = integerSpinner(
                0, 5_000, currentSubtitles.startPaddingMs(), 25);
        Spinner<Integer> endPadding = integerSpinner(
                0, 5_000, currentSubtitles.endPaddingMs(), 25);
        Spinner<Integer> speechGap = integerSpinner(
                0, 5_000, currentSubtitles.nextSpeechGapMs(), 25);
        Spinner<Double> maximumCps = doubleSpinner(
                5, 100, currentSubtitles.maximumCharactersPerSecond(), 1);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);

        addExecutableRow(grid, 0, "FFmpeg", ffmpeg, dialog, tr("settings.chooseFfmpeg"));
        addExecutableRow(grid, 1, "FFprobe", ffprobe, dialog, tr("settings.chooseFfprobe"));
        addExecutableRow(grid, 2, "whisper.cpp CLI", whisper, dialog, tr("settings.chooseWhisper"));
        addFileRow(grid, 3, tr("settings.whisperModel"), model, dialog, tr("settings.chooseModel"), tr("settings.whisperModel"), "*.bin");
        addFileRow(grid, 4, tr("settings.vadModel"), vadModel, dialog, tr("settings.chooseVad"), tr("settings.vadModel"), "*.bin");
        addDirectoryRow(grid, 5, tr("settings.temporaryFiles"), temporaryDirectory, dialog);
        grid.add(new Label(tr("settings.interfaceLanguage")), 0, 6);
        grid.add(uiLanguage, 1, 6);

        GridPane outputGrid = new GridPane();
        outputGrid.setHgap(12);
        outputGrid.setVgap(10);
        ColumnConstraints outputLabelColumn = new ColumnConstraints();
        outputLabelColumn.setMinWidth(250);
        ColumnConstraints outputFieldColumn = new ColumnConstraints();
        outputFieldColumn.setHgrow(Priority.ALWAYS);
        outputGrid.getColumnConstraints().addAll(outputLabelColumn, outputFieldColumn);
        outputGrid.add(new Label(tr("settings.saveSubtitles")), 0, 0);
        outputGrid.add(outputLocation, 1, 0);
        addDirectoryRow(outputGrid, 1, tr("settings.chosenFolder"), outputDirectory, dialog);
        outputDirectory.disableProperty().bind(outputLocation.valueProperty()
                .isNotEqualTo(OutputLocation.CUSTOM_FOLDER));

        GridPane subtitleGrid = new GridPane();
        subtitleGrid.setHgap(12);
        subtitleGrid.setVgap(10);
        ColumnConstraints subtitleLabelColumn = new ColumnConstraints();
        subtitleLabelColumn.setMinWidth(250);
        subtitleGrid.getColumnConstraints().addAll(subtitleLabelColumn, new ColumnConstraints());
        addSpinnerRow(subtitleGrid, 0, tr("settings.maxChars"), charactersPerLine);
        addSpinnerRow(subtitleGrid, 1, tr("settings.maxLines"), maximumLines);
        addSpinnerRow(subtitleGrid, 2, tr("settings.minDuration"), minimumDuration);
        addSpinnerRow(subtitleGrid, 3, tr("settings.startPadding"), startPadding);
        addSpinnerRow(subtitleGrid, 4, tr("settings.endPadding"), endPadding);
        addSpinnerRow(subtitleGrid, 5, tr("settings.nextGap"), speechGap);
        addSpinnerRow(subtitleGrid, 6, tr("settings.readingSpeed"), maximumCps);

        Label explanation = new Label(tr("settings.explanation"));
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted");

        Label subtitleHeading = new Label(tr("settings.subtitleHeading"));
        subtitleHeading.getStyleClass().add("section-title");
        Label subtitleExplanation = new Label(tr("settings.subtitleExplanation"));
        subtitleExplanation.setWrapText(true);
        subtitleExplanation.getStyleClass().add("muted");

        Label validationResult = new Label(tr("settings.notChecked"));
        validationResult.setWrapText(true);
        validationResult.setMinHeight(112);
        validationResult.getStyleClass().add("validation-result");
        Button validate = new Button(tr("settings.checkTools"));
        validate.getStyleClass().add("quiet-button");
        AtomicReference<Thread> validationThread = new AtomicReference<>();
        validate.setOnAction(event -> {
            Thread previous = validationThread.getAndSet(null);
            if (previous != null) {
                previous.interrupt();
            }
            validate.setDisable(true);
            validationResult.setText(tr("settings.checkingTools"));
            ApplicationSettings candidate = values(ffmpeg, ffprobe, whisper, model, vadModel,
                    temporaryDirectory, subtitlePreferences(charactersPerLine, maximumLines,
                            minimumDuration, startPadding, endPadding, speechGap, maximumCps));
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
        Label outputHeading = new Label(tr("settings.outputHeading"));
        outputHeading.getStyleClass().add("section-title");
        Label outputExplanation = new Label(tr("settings.outputExplanation"));
        outputExplanation.setWrapText(true);
        outputExplanation.getStyleClass().add("muted");

        VBox content = new VBox(16, explanation, grid, new Separator(), outputHeading,
                outputExplanation, outputGrid, new Separator(), subtitleHeading,
                subtitleExplanation, subtitleGrid, validationHeader, validationResult);
        content.setPadding(new Insets(8, 4, 4, 4));
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefViewportHeight(500);
        scrollPane.getStyleClass().add("components-scroll");
        dialog.getDialogPane().setContent(scrollPane);

        dialog.setResultConverter(button -> button == SAVE
                ? values(ffmpeg, ffprobe, whisper, model, vadModel, temporaryDirectory,
                        subtitlePreferences(charactersPerLine, maximumLines, minimumDuration,
                                startPadding, endPadding, speechGap, maximumCps),
                        new OutputPreferences(outputLocation.getValue(), outputDirectory.getText()),
                        uiLanguage.getValue())
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

    private static Spinner<Integer> integerSpinner(int minimum, int maximum, int value, int step) {
        Spinner<Integer> spinner = new Spinner<>(minimum, maximum, value, step);
        configureSpinner(spinner);
        return spinner;
    }

    private static Spinner<Double> doubleSpinner(double minimum, double maximum, double value, double step) {
        Spinner<Double> spinner = new Spinner<>(minimum, maximum, value, step);
        configureSpinner(spinner);
        return spinner;
    }

    private static void configureSpinner(Spinner<?> spinner) {
        spinner.setEditable(true);
        spinner.setPrefWidth(150);
        spinner.setMaxWidth(150);
    }

    private static void addSpinnerRow(GridPane grid, int row, String label, Spinner<?> spinner) {
        grid.add(new Label(label), 0, row);
        grid.add(spinner, 1, row);
    }

    private static void addExecutableRow(
            GridPane grid,
            int row,
            String label,
            TextField field,
            Dialog<?> dialog,
            String title
    ) {
        addFileRow(grid, row, label, field, dialog, title, tr("settings.executable"), "*.exe");
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
        Button browse = new Button(tr("common.browse"));
        browse.getStyleClass().add("quiet-button");
        browse.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().setAll(
                    new FileChooser.ExtensionFilter(filterName, filterPattern),
                    new FileChooser.ExtensionFilter(tr("common.allFiles"), "*.*")
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
        Button browse = new Button(tr("common.browse"));
        browse.getStyleClass().add("quiet-button");
        browse.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(tr("settings.chooseDirectory"));
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
            TextField temporaryDirectory,
            SubtitlePreferences subtitlePreferences
    ) {
        return values(ffmpeg, ffprobe, whisper, model, vadModel, temporaryDirectory,
                subtitlePreferences, OutputPreferences.defaults(), UiLanguage.ENGLISH);
    }

    private static ApplicationSettings values(
            TextField ffmpeg,
            TextField ffprobe,
            TextField whisper,
            TextField model,
            TextField vadModel,
            TextField temporaryDirectory,
            SubtitlePreferences subtitlePreferences,
            OutputPreferences outputPreferences,
            UiLanguage uiLanguage
    ) {
        return new ApplicationSettings(
                ApplicationSettings.CURRENT_SCHEMA_VERSION,
                ffmpeg.getText(),
                ffprobe.getText(),
                whisper.getText(),
                model.getText(),
                vadModel.getText(),
                temporaryDirectory.getText(),
                subtitlePreferences,
                outputPreferences,
                uiLanguage
        );
    }

    private static SubtitlePreferences subtitlePreferences(
            Spinner<Integer> charactersPerLine,
            Spinner<Integer> maximumLines,
            Spinner<Integer> minimumDuration,
            Spinner<Integer> startPadding,
            Spinner<Integer> endPadding,
            Spinner<Integer> speechGap,
            Spinner<Double> maximumCps
    ) {
        return new SubtitlePreferences(
                committedValue(charactersPerLine),
                committedValue(maximumLines),
                committedValue(minimumDuration),
                committedValue(startPadding),
                committedValue(endPadding),
                committedValue(speechGap),
                committedValue(maximumCps)
        );
    }

    private static <T> T committedValue(Spinner<T> spinner) {
        try {
            T value = spinner.getValueFactory().getConverter().fromString(spinner.getEditor().getText());
            spinner.getValueFactory().setValue(value);
        } catch (RuntimeException ignored) {
            // Keep the last valid value when an unfinished edit cannot be parsed.
        }
        return spinner.getValue();
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
