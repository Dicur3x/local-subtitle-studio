package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.subtitles.SpokenLanguage;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.github.dicur3x.lss.ui.I18n.tr;

public final class TranslationSetupDialog {
    public Optional<Result> showAndWait(Window owner, String sourceLanguage) {
        String sourceCode = sourceLanguage == null ? ""
                : sourceLanguage.strip().toLowerCase(java.util.Locale.ROOT);
        List<SpokenLanguage> all = SpokenLanguage.choices(I18n.locale());
        SpokenLanguage source = all.stream()
                .filter(language -> language.code().equals(sourceCode))
                .findFirst()
                .orElse(new SpokenLanguage(sourceCode.isBlank() ? "original" : sourceCode,
                        sourceCode.isBlank() ? tr("translation.unknownLanguage")
                                : sourceCode.toUpperCase(java.util.Locale.ROOT)));
        List<SpokenLanguage> targets = all.stream()
                .filter(language -> !SpokenLanguage.AUTO.code().equals(language.code()))
                .filter(language -> !sourceCode.equals(language.code()))
                .toList();
        SpokenLanguage initial = targets.stream()
                .filter(language -> language.code().equals("en".equals(sourceCode) ? "ru" : "en"))
                .findFirst().orElse(targets.getFirst());
        SearchableLanguagePicker targetPicker = new SearchableLanguagePicker(
                targets, initial, tr("main.otherLanguages"), tr("main.languageSearch"), 2);

        Dialog<Result> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(tr("translation.title"));
        dialog.setHeaderText(tr("translation.header"));
        dialog.setResizable(true);
        ButtonType translate = new ButtonType(
                tr("translation.start"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(translate,
                new ButtonType(tr("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"), "app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("settings-dialog");
        dialog.getDialogPane().setPrefWidth(590);

        Label explanation = new Label(tr("translation.explanation"));
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted");
        Label sourceLabel = new Label(tr("translation.sourceLanguage"));
        sourceLabel.getStyleClass().add("field-label");
        Label sourceValue = new Label(source.toString());
        sourceValue.getStyleClass().add("component-license");
        Label targetLabel = new Label(tr("translation.targetLanguage"));
        targetLabel.getStyleClass().add("field-label");
        Label performance = new Label(tr("translation.performanceHint"));
        performance.setWrapText(true);
        performance.getStyleClass().add("component-license");

        VBox content = new VBox(9,
                explanation,
                sourceLabel, sourceValue,
                targetLabel, targetPicker.control(),
                performance);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == translate
                ? new Result(targetPicker.selected().code()) : null);
        return dialog.showAndWait();
    }

    public record Result(String targetLanguage) {
        public Result {
            targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage").strip();
            if (targetLanguage.isEmpty()) {
                throw new IllegalArgumentException("Target language must not be blank");
            }
        }
    }
}
