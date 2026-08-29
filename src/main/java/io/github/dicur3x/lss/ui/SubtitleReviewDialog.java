package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.subtitles.CreatedSubtitles;
import io.github.dicur3x.lss.subtitles.SrtWriter;
import io.github.dicur3x.lss.subtitles.SubtitleCreationException;
import io.github.dicur3x.lss.subtitles.SubtitleCue;
import io.github.dicur3x.lss.subtitles.SubtitleIssue;
import io.github.dicur3x.lss.subtitles.SubtitleValidator;
import io.github.dicur3x.lss.subtitles.SubtitleWarningType;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static io.github.dicur3x.lss.ui.I18n.tr;

/** A focused editor that jumps directly between cues reported by subtitle validation. */
public final class SubtitleReviewDialog {
    private final Map<Long, String> editedText = new HashMap<>();
    private List<SubtitleCue> currentCues;
    private ReviewRow currentRow;

    public CreatedSubtitles showAndWait(Window owner, CreatedSubtitles result) {
        Objects.requireNonNull(result, "result");
        currentCues = new ArrayList<>(result.cues());

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(tr("review.title"));
        dialog.setHeaderText(tr("review.header", result.issues().size()));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().add(
                new ButtonType(tr("common.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.getDialogPane().setPrefSize(940, 650);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"), "app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().addAll("settings-dialog", "review-dialog");

        Label explanation = new Label(tr("review.explanation"));
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted");
        Label location = new Label(tr("review.file", result.file()));
        location.setWrapText(true);
        location.getStyleClass().add("component-license");

        ListView<ReviewRow> problemCues = new ListView<>();
        problemCues.getStyleClass().add("review-list");
        problemCues.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ReviewRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setText(null);
                    return;
                }
                String text = editedText.getOrDefault(row.cue().id(), row.cue().originalText());
                setText("#" + row.cue().id() + "  " + timeRange(row.cue()) + "\n"
                        + text + "\n" + issueNames(row.types()));
            }
        });

        Label cueHeading = new Label(tr("review.selectCue"));
        cueHeading.getStyleClass().add("section-title");
        Label cueTime = new Label();
        cueTime.getStyleClass().add("component-license");
        TextArea editor = new TextArea();
        editor.setWrapText(true);
        editor.setPromptText(tr("review.editorPrompt"));
        editor.getStyleClass().add("review-editor");
        VBox.setVgrow(editor, Priority.ALWAYS);
        Label status = new Label();
        status.setWrapText(true);
        status.getStyleClass().add("muted");

        problemCues.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldRow, newRow) -> {
                    storeEdit(oldRow, editor);
                    currentRow = newRow;
                    if (newRow == null) {
                        cueHeading.setText(tr("review.noProblems"));
                        cueTime.setText("");
                        editor.clear();
                        editor.setDisable(true);
                    } else {
                        cueHeading.setText(tr("review.cueHeading", newRow.cue().id()));
                        cueTime.setText(timeRange(newRow.cue()) + " · " + issueNames(newRow.types()));
                        editor.setDisable(false);
                        editor.setText(editedText.getOrDefault(
                                newRow.cue().id(), newRow.cue().originalText()));
                        editor.positionCaret(editor.getText().length());
                    }
                });

        Button previous = new Button(tr("review.previous"));
        Button next = new Button(tr("review.next"));
        Button save = new Button(tr("review.save"));
        final CreatedSubtitles[] reviewedResult = {result};
        previous.getStyleClass().add("quiet-button");
        next.getStyleClass().add("quiet-button");
        save.getStyleClass().add("primary-button");
        previous.setOnAction(event -> selectRelative(problemCues, -1));
        next.setOnAction(event -> selectRelative(problemCues, 1));
        save.setOnAction(event -> {
            storeEdit(currentRow, editor);
            try {
                List<SubtitleCue> updated = applyEdits(currentCues);
                new SrtWriter(result.subtitlePreferences()).replace(result.file(), updated);
                currentCues = updated;
                var validation = new SubtitleValidator(result.subtitlePreferences()).validate(updated);
                reviewedResult[0] = result.afterReview(updated, validation);
                populateRows(problemCues, updated, validation.issues());
                dialog.setHeaderText(tr("review.header", validation.issues().size()));
                status.getStyleClass().removeAll("validation-warning", "validation-success");
                status.getStyleClass().add("validation-success");
                status.setText(validation.issues().isEmpty()
                        ? tr("review.savedClean") : tr("review.savedRemaining", validation.issues().size()));
            } catch (SubtitleCreationException exception) {
                status.getStyleClass().removeAll("validation-warning", "validation-success");
                status.getStyleClass().add("validation-warning");
                status.setText(exception.getMessage());
            }
        });
        HBox actions = new HBox(10, previous, next, save);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox editorPane = new VBox(9, cueHeading, cueTime, editor, actions, status);
        editorPane.setPadding(new Insets(0, 0, 0, 12));
        SplitPane split = new SplitPane(problemCues, editorPane);
        split.setDividerPositions(0.43);
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox content = new VBox(12, explanation, location, split);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);

        populateRows(problemCues, currentCues, result.issues());
        dialog.showAndWait();
        return reviewedResult[0];
    }

    private static void populateRows(
            ListView<ReviewRow> list,
            List<SubtitleCue> cues,
            List<SubtitleIssue> issues
    ) {
        Map<Long, EnumSet<SubtitleWarningType>> byCue = new LinkedHashMap<>();
        issues.stream().sorted(Comparator.comparingLong(SubtitleIssue::cueId)).forEach(issue ->
                byCue.computeIfAbsent(issue.cueId(), ignored -> EnumSet.noneOf(SubtitleWarningType.class))
                        .add(issue.type()));
        Map<Long, SubtitleCue> cueById = new HashMap<>();
        cues.forEach(cue -> cueById.put(cue.id(), cue));
        List<ReviewRow> rows = byCue.entrySet().stream()
                .filter(entry -> cueById.containsKey(entry.getKey()))
                .map(entry -> new ReviewRow(cueById.get(entry.getKey()), entry.getValue()))
                .toList();
        list.setItems(FXCollections.observableArrayList(rows));
        if (!rows.isEmpty()) {
            list.getSelectionModel().selectFirst();
            list.scrollTo(0);
        }
    }

    private void storeEdit(ReviewRow row, TextArea editor) {
        if (row == null) {
            return;
        }
        String text = editor.getText() == null ? "" : editor.getText().replaceAll("\\s+", " ").strip();
        if (!text.isEmpty()) {
            editedText.put(row.cue().id(), text);
        }
    }

    private List<SubtitleCue> applyEdits(List<SubtitleCue> cues) {
        return cues.stream().map(cue -> new SubtitleCue(
                cue.id(), cue.start(), cue.end(), editedText.getOrDefault(cue.id(), cue.originalText()),
                cue.tokens())).toList();
    }

    private static void selectRelative(ListView<ReviewRow> list, int delta) {
        if (list.getItems().isEmpty()) {
            return;
        }
        int selected = Math.max(0, list.getSelectionModel().getSelectedIndex());
        int target = Math.max(0, Math.min(list.getItems().size() - 1, selected + delta));
        list.getSelectionModel().select(target);
        list.scrollTo(target);
    }

    private static String issueNames(EnumSet<SubtitleWarningType> types) {
        return types.stream().map(type -> tr("review.issue." + switch (type) {
            case LONG_LINE -> "longLine";
            case TOO_MANY_LINES -> "tooManyLines";
            case TOO_FAST -> "tooFast";
            case REPEATED_TEXT -> "repeatedText";
            case LOW_CONFIDENCE -> "lowConfidence";
            case MIXED_VOICE_OVER -> throw new IllegalArgumentException("Not a cue issue");
        })).collect(java.util.stream.Collectors.joining(" · "));
    }

    private static String timeRange(SubtitleCue cue) {
        return timestamp(cue.start()) + " — " + timestamp(cue.end());
    }

    private static String timestamp(Duration value) {
        long millis = value.toMillis();
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
                millis / 3_600_000,
                millis % 3_600_000 / 60_000,
                millis % 60_000 / 1_000,
                millis % 1_000);
    }

    private record ReviewRow(SubtitleCue cue, EnumSet<SubtitleWarningType> types) {
        private ReviewRow {
            Objects.requireNonNull(cue, "cue");
            types = EnumSet.copyOf(types);
        }
    }
}
