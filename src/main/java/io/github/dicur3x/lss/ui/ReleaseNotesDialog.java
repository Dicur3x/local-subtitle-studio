package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.components.ComponentRelease;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Objects;
import java.util.function.Consumer;

final class ReleaseNotesDialog {
    private static final ButtonType OFFICIAL_SOURCE = new ButtonType(
            "View on official source", ButtonBar.ButtonData.LEFT);

    private final Consumer<String> openLink;

    ReleaseNotesDialog(Consumer<String> openLink) {
        this.openLink = Objects.requireNonNull(openLink, "openLink");
    }

    void showAndWait(Window owner, ComponentRelease release) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(release.component().displayName() + " release notes");
        dialog.setHeaderText(release.component().displayName() + " " + release.version());
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().addAll(OFFICIAL_SOURCE, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(760, 560);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"), "app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().addAll("settings-dialog", "release-notes-dialog");

        Label explanation = new Label(
                "Changes published by the component's upstream project. The text is shown locally after a version check."
        );
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted");

        TextArea notes = new TextArea(ReleaseNotesText.toPlainText(release.releaseNotes()));
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefRowCount(22);
        notes.getStyleClass().add("release-notes-text");
        dialog.getDialogPane().setContent(new VBox(12, explanation, notes));

        Button source = (Button) dialog.getDialogPane().lookupButton(OFFICIAL_SOURCE);
        source.addEventFilter(ActionEvent.ACTION, event -> {
            openLink.accept(release.releaseNotesUri().toString());
            event.consume();
        });
        dialog.showAndWait();
    }
}
