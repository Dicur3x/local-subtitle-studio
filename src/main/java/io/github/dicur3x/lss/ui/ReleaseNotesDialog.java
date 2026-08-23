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

import static io.github.dicur3x.lss.ui.I18n.tr;

final class ReleaseNotesDialog {
    private static final ButtonType OFFICIAL_SOURCE = new ButtonType(
            tr("releaseNotes.officialSource"), ButtonBar.ButtonData.LEFT);

    private final Consumer<String> openLink;

    ReleaseNotesDialog(Consumer<String> openLink) {
        this.openLink = Objects.requireNonNull(openLink, "openLink");
    }

    void showAndWait(Window owner, ComponentRelease release) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(tr("releaseNotes.title", release.component().displayName()));
        dialog.setHeaderText(release.component().displayName() + " " + release.version());
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().addAll(OFFICIAL_SOURCE, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(760, 560);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"), "app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().addAll("settings-dialog", "release-notes-dialog");

        Label explanation = new Label(tr("releaseNotes.explanation"));
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
