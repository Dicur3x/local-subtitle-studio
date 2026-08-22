package io.github.dicur3x.lss.ui;

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
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MainView implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MainView.class.getName());
    private static final List<String> VIDEO_EXTENSIONS = List.of(
            "*.mkv", "*.mp4", "*.m4v", "*.mov", "*.avi", "*.webm", "*.ts", "*.mts", "*.m2ts"
    );

    private final MediaProbe mediaProbe;
    private final AudioTrackDisplayFormatter trackFormatter = new AudioTrackDisplayFormatter();
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    private final BorderPane root = new BorderPane();
    private final Label selectedFile = new Label();
    private final Label duration = new Label();
    private final Label status = new Label("Ready");
    private final Label error = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button cancelButton = new Button("Cancel");
    private final ComboBox<AudioTrack> audioTracks = new ComboBox<>();
    private final VBox mediaDetails = new VBox(14);
    private volatile Future<?> activeTask;

    public MainView(MediaProbe mediaProbe) {
        this.mediaProbe = Objects.requireNonNull(mediaProbe, "mediaProbe");
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
        Label intro = new Label("Drop one video below. We’ll inspect its audio tracks locally with ffprobe.");
        intro.getStyleClass().add("muted");

        VBox title = new VBox(8, brand, heading, intro);

        VBox dropZone = createDropZone();
        buildMediaDetails();

        VBox content = new VBox(26, title, dropZone, mediaDetails, createStatusBar());
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

        Label nextStep = new Label("Audio extraction and whisper.cpp transcription are the next MVP stage.");
        nextStep.getStyleClass().add("muted");

        mediaDetails.getChildren().setAll(
                new HBox(12, selectedFile, spacer(), duration),
                new VBox(7, audioLabel, audioTracks),
                nextStep
        );
        mediaDetails.setPadding(new Insets(20));
        mediaDetails.getStyleClass().add("details-card");
    }

    private HBox createStatusBar() {
        progress.setPrefSize(22, 22);
        progress.setMinSize(22, 22);
        cancelButton.getStyleClass().add("quiet-button");
        cancelButton.setOnAction(event -> cancelInspection());
        error.getStyleClass().add("error-text");
        error.setWrapText(true);

        HBox row = new HBox(10, progress, status, spacer(), cancelButton);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox wrapper = new VBox(8, row, error);
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

        cancelInspection();
        showLoadingState(file);
        activeTask = worker.submit(() -> {
            try {
                MediaInfo mediaInfo = mediaProbe.probe(file, Thread.currentThread()::isInterrupted);
                if (!Thread.currentThread().isInterrupted()) {
                    Platform.runLater(() -> showMediaInfo(mediaInfo));
                }
            } catch (CancellationException ignored) {
                Platform.runLater(this::showIdleState);
            } catch (Exception exception) {
                LOGGER.log(Level.SEVERE, "Media inspection failed", exception);
                Platform.runLater(() -> showError(exception.getMessage()));
            }
        });
    }

    private void showLoadingState(Path file) {
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
    }

    private void showMediaInfo(MediaInfo mediaInfo) {
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
        status.setText(mediaInfo.audioTracks().isEmpty()
                ? "No audio tracks found"
                : mediaInfo.audioTracks().size() + (mediaInfo.audioTracks().size() == 1
                ? " audio track found" : " audio tracks found"));
        activeTask = null;
    }

    private void showError(String message) {
        mediaDetails.setVisible(false);
        mediaDetails.setManaged(false);
        progress.setVisible(false);
        progress.setManaged(false);
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        status.setText("Could not inspect this file");
        error.setText(message == null || message.isBlank() ? "Unexpected media inspection error." : message);
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
        activeTask = null;
    }

    private void cancelInspection() {
        Future<?> task = activeTask;
        activeTask = null;
        if (task != null) {
            task.cancel(true);
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
        cancelInspection();
        worker.shutdownNow();
    }
}
