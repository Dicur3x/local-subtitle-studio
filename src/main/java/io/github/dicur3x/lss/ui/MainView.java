package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.audio.AudioExtractor;
import io.github.dicur3x.lss.audio.PreparedAudio;
import io.github.dicur3x.lss.media.MediaProbe;
import io.github.dicur3x.lss.media.model.AudioTrack;
import io.github.dicur3x.lss.media.model.MediaInfo;
import io.github.dicur3x.lss.subtitles.CreatedSubtitles;
import io.github.dicur3x.lss.subtitles.DialogueAudioMode;
import io.github.dicur3x.lss.subtitles.PipelineProgress;
import io.github.dicur3x.lss.subtitles.PipelineStage;
import io.github.dicur3x.lss.subtitles.RecognitionLoopException;
import io.github.dicur3x.lss.subtitles.SpokenLanguage;
import io.github.dicur3x.lss.subtitles.SubtitleCreationService;
import io.github.dicur3x.lss.subtitles.SubtitleReadiness;
import io.github.dicur3x.lss.subtitles.SubtitleWarning;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.github.dicur3x.lss.ui.I18n.tr;

public final class MainView implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MainView.class.getName());
    private static final List<String> VIDEO_EXTENSIONS = List.of(
            "*.mkv", "*.mp4", "*.m4v", "*.mov", "*.avi", "*.webm", "*.mpeg", "*.mpg",
            "*.ts", "*.mts", "*.m2ts", "*.vob", "*.wmv", "*.asf", "*.flv", "*.ogv",
            "*.3gp", "*.3g2", "*.mxf"
    );

    private final MediaProbe mediaProbe;
    private final AudioExtractor audioExtractor;
    private final SubtitleCreationService subtitleCreationService;
    private final Consumer<Window> componentsAction;
    private final Consumer<Window> settingsAction;
    private final AudioTrackDisplayFormatter trackFormatter = new AudioTrackDisplayFormatter();
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    private final BorderPane root = new BorderPane();
    private final Label selectedFile = new Label();
    private final Label duration = new Label();
    private final Label status = new Label(tr("common.ready"));
    private final Label error = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final Label progressPercent = new Label();
    private final Label progressStages = new Label();
    private final ScrollPane mainScrollPane = new ScrollPane();
    private final Button cancelButton = new Button(tr("common.cancel"));
    private final Button componentsButton = new Button(tr("main.components"));
    private final Button settingsButton = new Button(tr("main.advancedSettings"));
    private final Button createSubtitlesButton = new Button(tr("main.createSrt"));
    private final Button prepareAudioButton = new Button(tr("main.prepareAudio"));
    private final Button reviewButton = new Button(tr("main.reviewWarnings"));
    private final ComboBox<AudioTrack> audioTracks = new ComboBox<>();
    private final ComboBox<SpokenLanguage> spokenLanguages = new ComboBox<>();
    private final CheckBox voiceOverMode = new CheckBox(tr("main.voiceOverMode"));
    private final SpokenLanguage languageSeparator = new SpokenLanguage("separator", tr("main.otherLanguages"));
    private final List<SpokenLanguage> languageChoices = SpokenLanguage.choices(I18n.locale());
    private SpokenLanguage selectedSpokenLanguage = languageChoices.getFirst();
    private final VBox mediaDetails = new VBox(14);
    private final Label audioState = new Label();
    private final Label readinessState = new Label();
    private volatile Future<?> activeTask;
    private final AtomicLong operationGeneration = new AtomicLong();
    private MediaInfo currentMedia;
    private PreparedAudio preparedAudio;
    private CreatedSubtitles lastCreatedSubtitles;
    private boolean updatingLanguageChoices;

    public MainView(
            MediaProbe mediaProbe,
            AudioExtractor audioExtractor,
            SubtitleCreationService subtitleCreationService,
            Consumer<Window> componentsAction,
            Consumer<Window> settingsAction
    ) {
        this.mediaProbe = Objects.requireNonNull(mediaProbe, "mediaProbe");
        this.audioExtractor = Objects.requireNonNull(audioExtractor, "audioExtractor");
        this.subtitleCreationService = Objects.requireNonNull(
                subtitleCreationService, "subtitleCreationService");
        this.componentsAction = Objects.requireNonNull(componentsAction, "componentsAction");
        this.settingsAction = Objects.requireNonNull(settingsAction, "settingsAction");
        buildView();
    }

    public Parent root() {
        return root;
    }

    private void buildView() {
        Label brand = new Label("LOCAL SUBTITLE STUDIO");
        brand.getStyleClass().add("brand");
        Label heading = new Label(tr("main.heading"));
        heading.getStyleClass().add("heading");
        heading.setWrapText(true);
        Label intro = new Label(tr("main.intro"));
        intro.getStyleClass().add("muted");
        intro.setWrapText(true);

        VBox title = new VBox(8, brand, heading, intro);
        componentsButton.getStyleClass().add("primary-button");
        componentsButton.setOnAction(event -> {
            componentsAction.accept(componentsButton.getScene().getWindow());
            refreshReadiness();
        });
        settingsButton.getStyleClass().add("quiet-button");
        settingsButton.setOnAction(event -> {
            settingsAction.accept(settingsButton.getScene().getWindow());
            refreshReadiness();
        });
        HBox headerActions = new HBox(10, componentsButton, settingsButton);
        headerActions.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(16, title, headerActions);

        VBox dropZone = createDropZone();
        buildMediaDetails();

        VBox content = new VBox(26, header, dropZone, mediaDetails);
        content.setMaxWidth(820);
        content.setPadding(new Insets(42));

        StackPane centeredContent = new StackPane(content);
        centeredContent.setAlignment(Pos.TOP_CENTER);
        mainScrollPane.setContent(centeredContent);
        mainScrollPane.setFitToWidth(true);
        mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        mainScrollPane.getStyleClass().add("main-scroll");
        root.setCenter(mainScrollPane);
        HBox statusBar = createStatusBar();
        statusBar.setPadding(new Insets(0, 42, 24, 42));
        root.setBottom(statusBar);
        root.getStyleClass().add("app-root");
        status.getStyleClass().add("status-text");

        showIdleState();
    }

    private VBox createDropZone() {
        Label icon = new Label("＋");
        icon.getStyleClass().add("drop-icon");
        Label title = new Label(tr("main.dropTitle"));
        title.getStyleClass().add("drop-title");
        Label hint = new Label(tr("main.dropHint"));
        hint.getStyleClass().add("muted");
        Button chooseFile = new Button(tr("main.chooseVideo"));
        chooseFile.getStyleClass().add("primary-button");
        chooseFile.setOnAction(event -> chooseVideo(chooseFile.getScene().getWindow()));

        VBox dropZone = new VBox(10, icon, title, hint, chooseFile);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setMinHeight(230);
        dropZone.getStyleClass().add("drop-zone");

        dropZone.setOnDragOver(event -> {
            if (hasSingleFile(event.getDragboard())) {
                event.acceptTransferModes(TransferMode.COPY);
                dropZone.getStyleClass().add("drop-zone-active");
            }
            event.consume();
        });
        dropZone.setOnDragExited(event -> dropZone.getStyleClass().remove("drop-zone-active"));
        dropZone.setOnDragDropped(event -> {
            dropZone.getStyleClass().remove("drop-zone-active");
            boolean accepted = hasSingleFile(event.getDragboard());
            if (accepted) {
                inspect(event.getDragboard().getFiles().getFirst().toPath());
            }
            event.setDropCompleted(accepted);
            event.consume();
        });
        return dropZone;
    }

    private void buildMediaDetails() {
        selectedFile.getStyleClass().add("file-name");
        duration.getStyleClass().add("muted");

        Label audioLabel = new Label(tr("main.audioTrack"));
        audioLabel.getStyleClass().add("field-label");
        audioTracks.setMaxWidth(Double.MAX_VALUE);
        audioTracks.setButtonCell(createAudioCell());
        audioTracks.setCellFactory(listView -> createAudioCell());
        audioTracks.getSelectionModel().selectedItemProperty().addListener((observable, oldTrack, newTrack) -> {
            if (preparedAudio != null && oldTrack != null && newTrack != null
                    && oldTrack.streamIndex() != newTrack.streamIndex()) {
                closePreparedAudio();
                audioState.setText(tr("main.audioDecodeHint"));
                prepareAudioButton.setText(tr("main.prepareAudio"));
                status.setText(tr("main.audioTrackChanged"));
            }
        });

        Label languageLabel = new Label(tr("main.spokenLanguage"));
        languageLabel.getStyleClass().add("field-label");
        configureLanguagePicker();

        voiceOverMode.setWrapText(true);
        Label voiceOverHint = new Label(tr("main.voiceOverHint"));
        voiceOverHint.setWrapText(true);
        voiceOverHint.getStyleClass().add("muted");

        audioState.setText(tr("main.audioDecodeHint"));
        audioState.getStyleClass().add("muted");
        audioState.setWrapText(true);
        readinessState.setWrapText(true);
        createSubtitlesButton.getStyleClass().add("primary-button");
        createSubtitlesButton.setOnAction(event -> createSubtitles());
        createSubtitlesButton.setTooltip(new Tooltip(tr("main.createSrtTooltip")));
        prepareAudioButton.getStyleClass().add("quiet-button");
        prepareAudioButton.setOnAction(event -> prepareSelectedAudio());
        prepareAudioButton.setTooltip(new Tooltip(tr("main.prepareAudioTooltip")));

        FlowPane buttons = new FlowPane(10, 10, createSubtitlesButton, prepareAudioButton);
        buttons.setAlignment(Pos.CENTER_LEFT);
        audioState.setMaxWidth(Double.MAX_VALUE);
        VBox audioActionRow = new VBox(10, readinessState, audioState, buttons);

        mediaDetails.getChildren().setAll(
                new HBox(12, selectedFile, spacer(), duration),
                new VBox(7, audioLabel, audioTracks),
                new VBox(7, languageLabel, spokenLanguages),
                new VBox(5, voiceOverMode, voiceOverHint),
                audioActionRow
        );
        mediaDetails.setPadding(new Insets(20));
        mediaDetails.getStyleClass().add("details-card");
        refreshReadiness();
    }

    private HBox createStatusBar() {
        progress.setMaxWidth(Double.MAX_VALUE);
        progressPercent.getStyleClass().add("progress-percentage");
        progressStages.getStyleClass().add("pipeline-stages");
        progressStages.setWrapText(true);
        cancelButton.getStyleClass().add("quiet-button");
        cancelButton.setOnAction(event -> cancelFromUi());
        error.getStyleClass().add("error-text");
        error.setWrapText(true);
        reviewButton.getStyleClass().add("quiet-button");
        reviewButton.setVisible(false);
        reviewButton.setManaged(false);
        reviewButton.setOnAction(event -> {
            CreatedSubtitles created = lastCreatedSubtitles;
            if (created != null && !created.issues().isEmpty()) {
                CreatedSubtitles reviewed = new SubtitleReviewDialog().showAndWait(
                        reviewButton.getScene().getWindow(), created);
                lastCreatedSubtitles = reviewed;
                reviewButton.setVisible(!reviewed.issues().isEmpty());
                reviewButton.setManaged(!reviewed.issues().isEmpty());
            }
        });

        HBox row = new HBox(10, status, spacer(), progressPercent, cancelButton);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox wrapper = new VBox(8, row, progress, progressStages, error, reviewButton);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        wrapper.setMaxWidth(Double.MAX_VALUE);
        return new HBox(wrapper);
    }

    private ListCell<AudioTrack> createAudioCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AudioTrack item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : trackFormatter.format(item));
            }
        };
    }

    private void configureLanguagePicker() {
        spokenLanguages.setItems(FXCollections.observableArrayList(languageChoicesWithSeparator()));
        spokenLanguages.setCellFactory(listView -> createLanguageCell());
        spokenLanguages.setConverter(new StringConverter<>() {
            @Override
            public String toString(SpokenLanguage language) {
                return language == null || languageSeparator.equals(language) ? "" : language.toString();
            }

            @Override
            public SpokenLanguage fromString(String value) {
                return matchingLanguages(languageChoices, value, I18n.locale()).stream()
                        .findFirst()
                        .orElse(selectedSpokenLanguage);
            }
        });
        spokenLanguages.setEditable(true);
        spokenLanguages.setVisibleRowCount(14);
        spokenLanguages.setValue(selectedSpokenLanguage);
        spokenLanguages.setMaxWidth(Double.MAX_VALUE);
        spokenLanguages.getEditor().setPromptText(tr("main.languageSearch"));
        spokenLanguages.getEditor().textProperty().addListener((observable, oldText, newText) -> {
            if (!updatingLanguageChoices) {
                filterLanguages(newText);
            }
        });
        spokenLanguages.getEditor().focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (focused) {
                Platform.runLater(spokenLanguages.getEditor()::selectAll);
            } else if (!updatingLanguageChoices) {
                restoreLanguageSelection();
            }
        });
        spokenLanguages.setOnShowing(event -> {
            if (!updatingLanguageChoices
                    && spokenLanguages.getEditor().getText().equals(selectedSpokenLanguage.toString())) {
                restoreLanguageSelection();
            }
        });
        spokenLanguages.setOnAction(event -> {
            if (updatingLanguageChoices) {
                return;
            }
            SpokenLanguage chosen = spokenLanguages.getValue();
            if (chosen != null && !languageSeparator.equals(chosen)) {
                selectedSpokenLanguage = chosen;
            }
            restoreLanguageSelection();
        });
    }

    private ListCell<SpokenLanguage> createLanguageCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SpokenLanguage item, boolean empty) {
                super.updateItem(item, empty);
                boolean separator = !empty && languageSeparator.equals(item);
                getStyleClass().remove("language-separator");
                if (separator) {
                    getStyleClass().add("language-separator");
                }
                setDisable(separator);
                setMouseTransparent(separator);
                setText(empty || item == null ? null
                        : separator ? "────────  " + item.displayName() : item.toString());
            }
        };
    }

    private List<SpokenLanguage> languageChoicesWithSeparator() {
        List<SpokenLanguage> displayed = new ArrayList<>(languageChoices.size() + 1);
        int promotedCount = Math.min(3, languageChoices.size());
        displayed.addAll(languageChoices.subList(0, promotedCount));
        if (languageChoices.size() > promotedCount) {
            displayed.add(languageSeparator);
            displayed.addAll(languageChoices.subList(promotedCount, languageChoices.size()));
        }
        return displayed;
    }

    private void filterLanguages(String query) {
        SpokenLanguage currentValue = spokenLanguages.getValue();
        if (query != null && (query.equals(selectedSpokenLanguage.toString())
                || currentValue != null && !languageSeparator.equals(currentValue)
                && query.equals(currentValue.toString()))) {
            return;
        }
        String normalized = query == null ? "" : query.strip().toLowerCase(I18n.locale());
        List<SpokenLanguage> filtered = normalized.isEmpty()
                ? languageChoicesWithSeparator()
                : matchingLanguages(languageChoices, normalized, I18n.locale());
        updatingLanguageChoices = true;
        spokenLanguages.getItems().setAll(filtered);
        spokenLanguages.setValue(null);
        spokenLanguages.getEditor().setText(query == null ? "" : query);
        spokenLanguages.getEditor().positionCaret(spokenLanguages.getEditor().getText().length());
        updatingLanguageChoices = false;
        if (spokenLanguages.getEditor().isFocused()) {
            showLanguageResults();
        }
    }

    static List<SpokenLanguage> matchingLanguages(
            List<SpokenLanguage> choices,
            String query,
            Locale locale
    ) {
        String normalized = query == null ? "" : query.strip().toLowerCase(locale);
        if (normalized.isEmpty()) {
            return List.copyOf(choices);
        }
        return choices.stream()
                .filter(language -> language.toString().toLowerCase(locale).contains(normalized)
                        || language.code().contains(normalized))
                .toList();
    }

    private void showLanguageResults() {
        Platform.runLater(() -> {
            spokenLanguages.show();
            spokenLanguages.getEditor().requestFocus();
            spokenLanguages.getEditor().positionCaret(spokenLanguages.getEditor().getText().length());
        });
    }

    private void restoreLanguageSelection() {
        updatingLanguageChoices = true;
        spokenLanguages.getItems().setAll(languageChoicesWithSeparator());
        spokenLanguages.setValue(selectedSpokenLanguage);
        spokenLanguages.getEditor().setText(selectedSpokenLanguage.toString());
        updatingLanguageChoices = false;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void chooseVideo(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(tr("main.chooseVideoTitle"));
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter(tr("main.videoFiles"), VIDEO_EXTENSIONS),
                new FileChooser.ExtensionFilter(tr("common.allFiles"), "*.*")
        );
        File file = chooser.showOpenDialog(owner);
        if (file != null) {
            inspect(file.toPath());
        }
    }

    public void inspect(Path file) {
        if (!Files.isRegularFile(file)) {
            showError(tr("main.singleReadableVideo"));
            return;
        }

        long operationId = beginOperation();
        closePreparedAudio();
        showLoadingState(file);
        activeTask = worker.submit(() -> {
            try {
                MediaInfo mediaInfo = mediaProbe.probe(file, Thread.currentThread()::isInterrupted);
                if (!Thread.currentThread().isInterrupted()) {
                    runOnUiIfCurrent(operationId, () -> showMediaInfo(mediaInfo));
                }
            } catch (CancellationException ignored) {
                runOnUiIfCurrent(operationId, this::showIdleState);
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Media inspection failed", exception);
                runOnUiIfCurrent(operationId, () -> showError(exception.getMessage()));
            }
        });
    }

    private void showLoadingState(Path file) {
        currentMedia = null;
        selectedFile.setText(file.getFileName().toString());
        duration.setText("");
        mediaDetails.setVisible(false);
        mediaDetails.setManaged(false);
        audioTracks.getItems().clear();
        error.setText("");
        showIndeterminateProgress();
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        status.setText(tr("main.inspecting"));
        setControlsBusy(true);
    }

    private void showMediaInfo(MediaInfo mediaInfo) {
        currentMedia = mediaInfo;
        selectedFile.setText(mediaInfo.file().getFileName().toString());
        duration.setText(formatDuration(mediaInfo.duration()));
        audioTracks.setItems(FXCollections.observableArrayList(mediaInfo.audioTracks()));
        if (!mediaInfo.audioTracks().isEmpty()) {
            audioTracks.getSelectionModel().selectFirst();
        }
        mediaDetails.setVisible(true);
        mediaDetails.setManaged(true);
        hideProgress();
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        error.setText("");
        audioState.setText(tr("main.audioDecodeHint"));
        prepareAudioButton.setText(tr("main.prepareAudio"));
        setControlsBusy(false);
        createSubtitlesButton.setDisable(mediaInfo.audioTracks().isEmpty());
        prepareAudioButton.setDisable(mediaInfo.audioTracks().isEmpty());
        status.setText(mediaInfo.audioTracks().isEmpty()
                ? tr("main.noAudioTracks")
                : tr("main.audioTracksFound", mediaInfo.audioTracks().size()));
        Platform.runLater(() -> mainScrollPane.setVvalue(1));
        activeTask = null;
    }

    private void prepareSelectedAudio() {
        MediaInfo media = currentMedia;
        AudioTrack selectedTrack = audioTracks.getSelectionModel().getSelectedItem();
        if (media == null || selectedTrack == null) {
            showOperationError(tr("main.audioPreparationFailed"), tr("main.chooseAudioFirst"));
            return;
        }

        long operationId = beginOperation();
        closePreparedAudio();
        error.setText("");
        showIndeterminateProgress();
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        status.setText(tr("main.preparingPcm"));
        audioState.setText(tr("main.decodingOriginalSafe"));
        setControlsBusy(true);

        activeTask = worker.submit(() -> {
            try {
                PreparedAudio result = audioExtractor.extract(
                        media.file(), selectedTrack.streamIndex(), Thread.currentThread()::isInterrupted);
                if (Thread.currentThread().isInterrupted()) {
                    result.close();
                } else {
                    Platform.runLater(() -> {
                        if (isCurrent(operationId)) {
                            showPreparedAudio(result);
                        } else {
                            closePreparedAudio(result);
                        }
                    });
                }
            } catch (CancellationException ignored) {
                runOnUiIfCurrent(operationId,
                        () -> restoreMediaReadyState(tr("main.audioPreparationCancelled")));
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Audio preparation failed", exception);
                runOnUiIfCurrent(operationId,
                        () -> showOperationError(tr("main.audioPreparationFailed"), exception.getMessage()));
            }
        });
    }

    private void createSubtitles() {
        MediaInfo media = currentMedia;
        AudioTrack selectedTrack = audioTracks.getSelectionModel().getSelectedItem();
        SpokenLanguage selectedLanguage = selectedSpokenLanguage;
        if (media == null || selectedTrack == null || selectedLanguage == null) {
            showOperationError(tr("main.subtitleCreationFailed"), tr("main.chooseTrackLanguage"));
            return;
        }
        if (voiceOverMode.isSelected() && SpokenLanguage.AUTO.code().equals(selectedLanguage.code())) {
            showOperationError(tr("main.subtitleCreationFailed"), tr("main.voiceOverChooseLanguage"));
            return;
        }
        SubtitleReadiness readiness = subtitleCreationService.readiness();
        if (!readiness.ready()) {
            refreshReadiness();
            showOperationError(tr("main.setupRequired"), tr("main.openComponentsFirst"));
            return;
        }
        DialogueAudioMode audioMode = voiceOverMode.isSelected()
                ? DialogueAudioMode.MIXED_VOICE_OVER : DialogueAudioMode.STANDARD;

        long operationId = beginOperation();
        closePreparedAudio();
        error.setText("");
        updatePipelineProgress(PipelineProgress.at(
                PipelineStage.PREPARING_AUDIO, 0, tr("main.preparingRecognitionAudio")));
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        status.setText(tr("main.startingCreation"));
        audioState.setText(tr("main.localRecognitionHint"));
        setControlsBusy(true);

        activeTask = worker.submit(() -> {
            try {
                CreatedSubtitles result = subtitleCreationService.create(
                        media.file(), selectedTrack.streamIndex(), selectedLanguage.code(), audioMode,
                        Thread.currentThread()::isInterrupted,
                        pipelineProgress -> runOnUiIfCurrent(
                                operationId, () -> updatePipelineProgress(pipelineProgress)));
                runOnUiIfCurrent(operationId, () -> showCreatedSubtitles(result));
            } catch (CancellationException ignored) {
                runOnUiIfCurrent(operationId, () -> restoreMediaReadyState(tr("main.subtitleCreationCancelled")));
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Subtitle creation failed", exception);
                runOnUiIfCurrent(operationId,
                        () -> showOperationError(tr("main.subtitleCreationFailed"),
                                localizedCreationFailure(exception)));
            }
        });
    }

    private static String localizedCreationFailure(Exception exception) {
        if (exception instanceof RecognitionLoopException loop) {
            return tr("main.recognitionLoop", formatDuration(loop.position()));
        }
        return exception.getMessage();
    }

    private void showCreatedSubtitles(CreatedSubtitles result) {
        updatePipelineProgress(PipelineProgress.complete(tr("main.subtitlesReady")));
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        setControlsBusy(false);
        audioState.setText(tr("main.savedTo", result.file()));
        status.setText(tr("main.createdSummary", result.cueCount(), result.language(),
                result.warnings().isEmpty() ? tr("main.checksPassed")
                        : tr("main.warningCount", result.warnings().size())));
        showNotice(result.warnings().isEmpty() ? "" : tr("main.checkPrefix") + " "
                        + result.warnings().stream().map(MainView::localizedWarning)
                                .collect(java.util.stream.Collectors.joining(" ")),
                !result.warnings().isEmpty());
        lastCreatedSubtitles = result;
        reviewButton.setVisible(!result.issues().isEmpty());
        reviewButton.setManaged(!result.issues().isEmpty());
        activeTask = null;
    }

    private void showPreparedAudio(PreparedAudio result) {
        preparedAudio = result;
        hideProgress();
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        setControlsBusy(false);
        prepareAudioButton.setText(tr("main.prepareAudioAgain"));
        try {
            audioState.setText(String.format(Locale.ROOT,
                    tr("main.audioReadySize"), result.size() / (1024d * 1024d)));
        } catch (IOException exception) {
            audioState.setText(tr("main.audioReady"));
        }
        status.setText(tr("main.audioPreparationComplete"));
        error.setText("");
        activeTask = null;
    }

    private void restoreMediaReadyState(String message) {
        hideProgress();
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        setControlsBusy(false);
        audioState.setText(tr("main.audioDecodeHint"));
        status.setText(message);
        activeTask = null;
    }

    private void showOperationError(String title, String message) {
        hideProgress();
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        setControlsBusy(false);
        status.setText(title);
        showNotice(message == null || message.isBlank() ? tr("main.unexpectedOperationError") : message, false);
        activeTask = null;
    }

    private void showError(String message) {
        currentMedia = null;
        mediaDetails.setVisible(false);
        mediaDetails.setManaged(false);
        hideProgress();
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        status.setText(tr("main.couldNotInspect"));
        showNotice(message == null || message.isBlank() ? tr("main.unexpectedInspectionError") : message, false);
        setControlsBusy(false);
        mainScrollPane.setVvalue(0);
        activeTask = null;
    }

    private void showIdleState() {
        hideProgress();
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        if (audioTracks.getItems().isEmpty()) {
            mediaDetails.setVisible(false);
            mediaDetails.setManaged(false);
            status.setText(tr("common.ready"));
            mainScrollPane.setVvalue(0);
        }
        setControlsBusy(false);
        activeTask = null;
    }

    public void refreshReadiness() {
        SubtitleReadiness readiness = subtitleCreationService.readiness();
        readinessState.getStyleClass().removeAll("validation-success", "validation-warning");
        if (readiness.ready()) {
            readinessState.setText(tr("main.readinessReady"));
            readinessState.getStyleClass().add("validation-success");
        } else {
            readinessState.setText(tr("main.readinessMissing"));
            readinessState.getStyleClass().add("validation-warning");
        }
    }

    private long beginOperation() {
        long operationId = operationGeneration.incrementAndGet();
        cancelTask();
        lastCreatedSubtitles = null;
        reviewButton.setVisible(false);
        reviewButton.setManaged(false);
        return operationId;
    }

    private void cancelFromUi() {
        operationGeneration.incrementAndGet();
        cancelTask();
        if (currentMedia == null) {
            showIdleState();
        } else {
            restoreMediaReadyState(tr("main.operationCancelled"));
        }
    }

    private void cancelTask() {
        Future<?> task = activeTask;
        activeTask = null;
        if (task != null) {
            task.cancel(true);
        }
    }

    private void runOnUiIfCurrent(long operationId, Runnable action) {
        Platform.runLater(() -> {
            if (isCurrent(operationId)) {
                action.run();
            }
        });
    }

    private boolean isCurrent(long operationId) {
        return operationGeneration.get() == operationId;
    }

    private void showIndeterminateProgress() {
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progress.setVisible(true);
        progress.setManaged(true);
        progressPercent.setText(tr("common.working"));
        progressPercent.setVisible(true);
        progressPercent.setManaged(true);
        progressStages.setVisible(false);
        progressStages.setManaged(false);
    }

    private void updatePipelineProgress(PipelineProgress pipelineProgress) {
        progress.setProgress(pipelineProgress.overallPercent() / 100d);
        progress.setVisible(true);
        progress.setManaged(true);
        progressPercent.setText(pipelineProgress.overallPercent() + "%");
        progressPercent.setVisible(true);
        progressPercent.setManaged(true);
        progressStages.setText(formatPipelineStages(pipelineProgress));
        progressStages.setVisible(true);
        progressStages.setManaged(true);
        status.setText(tr("pipeline.status."
                + pipelineProgress.stage().name().toLowerCase(Locale.ROOT)));
    }

    private void hideProgress() {
        progress.setVisible(false);
        progress.setManaged(false);
        progressPercent.setVisible(false);
        progressPercent.setManaged(false);
        progressStages.setVisible(false);
        progressStages.setManaged(false);
    }

    private void showNotice(String message, boolean warning) {
        error.setText(message);
        error.getStyleClass().removeAll("error-text", "warning-text");
        error.getStyleClass().add(warning ? "warning-text" : "error-text");
    }

    private static String formatPipelineStages(PipelineProgress current) {
        List<PipelineStage> displayed = List.of(
                PipelineStage.PREPARING_AUDIO,
                PipelineStage.TRANSCRIBING,
                PipelineStage.OPTIMIZING,
                PipelineStage.VALIDATING,
                PipelineStage.WRITING
        );
        return displayed.stream().map(stage -> {
            if (current.stage() == PipelineStage.COMPLETE || stage.ordinal() < current.stage().ordinal()) {
                return "✓ " + stageName(stage);
            }
            if (stage == current.stage()) {
                return "● " + stageName(stage) + " " + current.stagePercent() + "%";
            }
            return "○ " + stageName(stage);
        }).collect(java.util.stream.Collectors.joining("   "));
    }

    private static String stageName(PipelineStage stage) {
        return tr("pipeline." + stage.name().toLowerCase(Locale.ROOT));
    }

    private void setControlsBusy(boolean busy) {
        componentsButton.setDisable(busy);
        settingsButton.setDisable(busy);
        audioTracks.setDisable(busy);
        spokenLanguages.setDisable(busy);
        voiceOverMode.setDisable(busy);
        createSubtitlesButton.setDisable(busy);
        prepareAudioButton.setDisable(busy);
        reviewButton.setDisable(busy);
    }

    private static String localizedWarning(SubtitleWarning warning) {
        String key = switch (warning.type()) {
            case LONG_LINE -> "warning.longLine";
            case TOO_MANY_LINES -> "warning.tooManyLines";
            case TOO_FAST -> "warning.tooFast";
            case REPEATED_TEXT -> "warning.repeatedText";
            case LOW_CONFIDENCE -> "warning.lowConfidence";
            case MIXED_VOICE_OVER -> "warning.mixedVoiceOver";
        };
        return tr(key, warning.count());
    }

    private void closePreparedAudio() {
        PreparedAudio audio = preparedAudio;
        preparedAudio = null;
        if (audio != null) {
            try {
                audio.close();
            } catch (IOException exception) {
                LOGGER.log(Level.WARNING, "Could not remove temporary audio", exception);
            }
        }
    }

    private static void closePreparedAudio(PreparedAudio audio) {
        try {
            audio.close();
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Could not remove stale temporary audio", exception);
        }
    }

    private static boolean hasSingleFile(Dragboard dragboard) {
        return dragboard.hasFiles() && dragboard.getFiles().size() == 1;
    }

    private static String formatDuration(Duration value) {
        long seconds = value.toSeconds();
        long hours = seconds / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds);
    }

    @Override
    public void close() {
        operationGeneration.incrementAndGet();
        cancelTask();
        closePreparedAudio();
        worker.shutdownNow();
    }
}
