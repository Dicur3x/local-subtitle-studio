package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.audio.AudioExtractor;
import io.github.dicur3x.lss.audio.PreparedAudio;
import io.github.dicur3x.lss.media.MediaProbe;
import io.github.dicur3x.lss.media.model.AudioTrack;
import io.github.dicur3x.lss.media.model.MediaInfo;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

public final class MainView implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MainView.class.getName());
    private static final List<String> VIDEO_EXTENSIONS = List.of(
            "*.mkv", "*.mp4", "*.m4v", "*.mov", "*.avi", "*.webm", "*.ts", "*.mts", "*.m2ts"
    );

    private final MediaProbe mediaProbe;
    private final AudioExtractor audioExtractor;
    private final Consumer<Window> componentsAction;
    private final Consumer<Window> settingsAction;
    private final AudioTrackDisplayFormatter trackFormatter = new AudioTrackDisplayFormatter();
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    private final BorderPane root = new BorderPane();
    private final Label selectedFile = new Label();
    private final Label duration = new Label();
    private final Label status = new Label("Ready");
    private final Label error = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button cancelButton = new Button("Cancel");
    private final Button componentsButton = new Button("Components");
    private final Button settingsButton = new Button("Advanced settings");
    private final Button prepareAudioButton = new Button("Prepare audio");
    private final ComboBox<AudioTrack> audioTracks = new ComboBox<>();
    private final VBox mediaDetails = new VBox(14);
    private final Label audioState = new Label();
    private volatile Future<?> activeTask;
    private final AtomicLong operationGeneration = new AtomicLong();
    private MediaInfo currentMedia;
    private PreparedAudio preparedAudio;

    public MainView(
            MediaProbe mediaProbe,
            AudioExtractor audioExtractor,
            Consumer<Window> componentsAction,
            Consumer<Window> settingsAction
    ) {
        this.mediaProbe = Objects.requireNonNull(mediaProbe, "mediaProbe");
        this.audioExtractor = Objects.requireNonNull(audioExtractor, "audioExtractor");
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
        Label heading = new Label("Create subtitles without uploading your video");
        heading.getStyleClass().add("heading");
        heading.setWrapText(true);
        Label intro = new Label("Drop one video below. We’ll inspect its audio tracks locally with ffprobe.");
        intro.getStyleClass().add("muted");
        intro.setWrapText(true);

        VBox title = new VBox(8, brand, heading, intro);
        componentsButton.getStyleClass().add("primary-button");
        componentsButton.setOnAction(event -> componentsAction.accept(componentsButton.getScene().getWindow()));
        settingsButton.getStyleClass().add("quiet-button");
        settingsButton.setOnAction(event -> settingsAction.accept(settingsButton.getScene().getWindow()));
        HBox headerActions = new HBox(10, componentsButton, settingsButton);
        headerActions.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(16, title, headerActions);

        VBox dropZone = createDropZone();
        buildMediaDetails();

        VBox content = new VBox(26, header, dropZone, mediaDetails, createStatusBar());
        content.setMaxWidth(820);
        content.setPadding(new Insets(42));

        root.setCenter(content);
        BorderPane.setAlignment(content, Pos.TOP_CENTER);
        root.getStyleClass().add("app-root");
        status.getStyleClass().add("status-text");

        showIdleState();
    }

    private VBox createDropZone() {
        Label icon = new Label("＋");
        icon.getStyleClass().add("drop-icon");
        Label title = new Label("Drop video file here");
        title.getStyleClass().add("drop-title");
        Label hint = new Label("MKV, MP4, MOV, AVI, WebM, TS and other ffprobe-compatible containers");
        hint.getStyleClass().add("muted");
        Button chooseFile = new Button("Choose video");
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

        Label audioLabel = new Label("Audio track");
        audioLabel.getStyleClass().add("field-label");
        audioTracks.setMaxWidth(Double.MAX_VALUE);
        audioTracks.setButtonCell(createAudioCell());
        audioTracks.setCellFactory(listView -> createAudioCell());
        audioTracks.getSelectionModel().selectedItemProperty().addListener((observable, oldTrack, newTrack) -> {
            if (preparedAudio != null && oldTrack != null && newTrack != null
                    && oldTrack.streamIndex() != newTrack.streamIndex()) {
                closePreparedAudio();
                audioState.setText("The selected track will be decoded to lossless 16 kHz mono PCM for whisper.cpp.");
                prepareAudioButton.setText("Prepare audio");
                status.setText("Audio track changed");
            }
        });

        audioState.setText("The selected track will be decoded to lossless 16 kHz mono PCM for whisper.cpp.");
        audioState.getStyleClass().add("muted");
        audioState.setWrapText(true);
        prepareAudioButton.getStyleClass().add("primary-button");
        prepareAudioButton.setOnAction(event -> prepareSelectedAudio());

        HBox audioActionRow = new HBox(12, audioState, prepareAudioButton);
        audioActionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(audioState, Priority.ALWAYS);
        audioState.setMaxWidth(Double.MAX_VALUE);

        mediaDetails.getChildren().setAll(
                new HBox(12, selectedFile, spacer(), duration),
                new VBox(7, audioLabel, audioTracks),
                audioActionRow
        );
        mediaDetails.setPadding(new Insets(20));
        mediaDetails.getStyleClass().add("details-card");
    }

    private HBox createStatusBar() {
        progress.setPrefSize(22, 22);
        progress.setMinSize(22, 22);
        cancelButton.getStyleClass().add("quiet-button");
        cancelButton.setOnAction(event -> cancelFromUi());
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        HBox row = new HBox(10, progress, status, spacer(), cancelButton);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox wrapper = new VBox(8, row, error);
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

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void chooseVideo(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a video file");
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Video files", VIDEO_EXTENSIONS),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        File file = chooser.showOpenDialog(owner);
        if (file != null) {
            inspect(file.toPath());
        }
    }

    public void inspect(Path file) {
        if (!Files.isRegularFile(file)) {
            showError("Drop a single readable video file.");
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
        progress.setVisible(true);
        progress.setManaged(true);
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        status.setText("Inspecting audio tracks…");
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
        progress.setVisible(false);
        progress.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        error.setText("");
        audioState.setText("The selected track will be decoded to lossless 16 kHz mono PCM for whisper.cpp.");
        prepareAudioButton.setText("Prepare audio");
        setControlsBusy(false);
        prepareAudioButton.setDisable(mediaInfo.audioTracks().isEmpty());
        status.setText(mediaInfo.audioTracks().isEmpty()
                ? "No audio tracks found"
                : mediaInfo.audioTracks().size() + (mediaInfo.audioTracks().size() == 1
                ? " audio track found" : " audio tracks found"));
        activeTask = null;
    }

    private void prepareSelectedAudio() {
        MediaInfo media = currentMedia;
        AudioTrack selectedTrack = audioTracks.getSelectionModel().getSelectedItem();
        if (media == null || selectedTrack == null) {
            showOperationError("Choose an audio track first.");
            return;
        }

        long operationId = beginOperation();
        closePreparedAudio();
        error.setText("");
        progress.setVisible(true);
        progress.setManaged(true);
        cancelButton.setVisible(true);
        cancelButton.setManaged(true);
        status.setText("Preparing 16 kHz mono PCM audio…");
        audioState.setText("Decoding the selected track. The original video remains unchanged.");
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
                        () -> restoreMediaReadyState("Audio preparation cancelled"));
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Audio preparation failed", exception);
                runOnUiIfCurrent(operationId, () -> showOperationError(exception.getMessage()));
            }
        });
    }

    private void showPreparedAudio(PreparedAudio result) {
        preparedAudio = result;
        progress.setVisible(false);
        progress.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        setControlsBusy(false);
        prepareAudioButton.setText("Prepare again");
        try {
            audioState.setText(String.format(Locale.ROOT,
                    "Audio ready: 16 kHz · mono · 16 bit PCM · %.1f MB temporary file",
                    result.size() / (1024d * 1024d)));
        } catch (IOException exception) {
            audioState.setText("Audio ready: 16 kHz · mono · 16 bit PCM");
        }
        status.setText("Audio preparation complete");
        error.setText("");
        activeTask = null;
    }

    private void restoreMediaReadyState(String message) {
        progress.setVisible(false);
        progress.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        setControlsBusy(false);
        audioState.setText("The selected track will be decoded to lossless 16 kHz mono PCM for whisper.cpp.");
        status.setText(message);
        activeTask = null;
    }

    private void showOperationError(String message) {
        progress.setVisible(false);
        progress.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        setControlsBusy(false);
        status.setText("Audio preparation failed");
        error.setText(message == null || message.isBlank() ? "Unexpected audio preparation error." : message);
        activeTask = null;
    }

    private void showError(String message) {
        currentMedia = null;
        mediaDetails.setVisible(false);
        mediaDetails.setManaged(false);
        progress.setVisible(false);
        progress.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        status.setText("Could not inspect this file");
        error.setText(message == null || message.isBlank() ? "Unexpected media inspection error." : message);
        setControlsBusy(false);
        activeTask = null;
    }

    private void showIdleState() {
        progress.setVisible(false);
        progress.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        if (audioTracks.getItems().isEmpty()) {
            mediaDetails.setVisible(false);
            mediaDetails.setManaged(false);
            status.setText("Ready");
        }
        setControlsBusy(false);
        activeTask = null;
    }

    private long beginOperation() {
        long operationId = operationGeneration.incrementAndGet();
        cancelTask();
        return operationId;
    }

    private void cancelFromUi() {
        operationGeneration.incrementAndGet();
        cancelTask();
        if (currentMedia == null) {
            showIdleState();
        } else {
            restoreMediaReadyState("Operation cancelled");
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

    private void setControlsBusy(boolean busy) {
        componentsButton.setDisable(busy);
        settingsButton.setDisable(busy);
        audioTracks.setDisable(busy);
        prepareAudioButton.setDisable(busy);
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
