package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.settings.OutputLocation;
import io.github.dicur3x.lss.settings.OutputPreferences;
import io.github.dicur3x.lss.settings.UiLanguage;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.Optional;
import java.util.Objects;

public final class FirstRunDialog {
    private static final ButtonType CONTINUE = new ButtonType(
            "Continue / Продолжить", ButtonBar.ButtonData.OK_DONE);

    public Optional<Result> showAndWait() {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle("Welcome / Добро пожаловать");
        dialog.setHeaderText("Local Subtitle Studio");
        ButtonType cancel = new ButtonType("Cancel / Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(CONTINUE, cancel);
        dialog.getDialogPane().setPrefWidth(610);
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/io/github/dicur3x/lss/app.css"), "app.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("settings-dialog");

        Label intro = new Label(
                "Choose two essentials. Everything can be changed later.\n"
                        + "Выберите два основных параметра. Позже их можно изменить.");
        intro.setWrapText(true);

        Label languageLabel = new Label("Interface language / Язык интерфейса");
        ComboBox<UiLanguage> language = new ComboBox<>();
        language.getItems().setAll(UiLanguage.values());
        language.getSelectionModel().select(UiLanguage.ENGLISH);
        language.setMaxWidth(Double.MAX_VALUE);

        Label outputLabel = new Label("Subtitle location / Куда сохранять субтитры");
        ComboBox<OutputLocation> output = new ComboBox<>();
        output.getItems().setAll(OutputLocation.BESIDE_VIDEO, OutputLocation.SUBS_FOLDER);
        output.getSelectionModel().select(OutputLocation.BESIDE_VIDEO);
        output.setMaxWidth(Double.MAX_VALUE);
        output.setConverter(new StringConverter<>() {
            @Override
            public String toString(OutputLocation value) {
                if (value == null) {
                    return "";
                }
                boolean russian = language.getValue() == UiLanguage.RUSSIAN;
                return switch (value) {
                    case BESIDE_VIDEO -> russian ? "Рядом с видео" : "Beside the video";
                    case SUBS_FOLDER -> russian ? "В папке Subs рядом с видео" : "In a Subs folder beside the video";
                    case CUSTOM_FOLDER -> russian ? "В выбранной папке" : "In a chosen folder";
                };
            }

            @Override
            public OutputLocation fromString(String value) {
                return output.getValue();
            }
        });
        language.valueProperty().addListener((observable, oldValue, newValue) -> output.setConverter(output.getConverter()));

        CheckBox openComponents = new CheckBox(
                "Open component setup next / Затем открыть установку компонентов");
        openComponents.setSelected(true);

        Label storage = new Label(
                "Settings and managed components are stored per user in Local AppData. "
                        + "The app does not change the system PATH and does not need administrator rights.\n"
                        + "Настройки и компоненты хранятся в Local AppData текущего пользователя. "
                        + "Приложение не меняет системный PATH и не требует прав администратора.");
        storage.setWrapText(true);
        storage.getStyleClass().add("muted");

        VBox content = new VBox(12, intro, languageLabel, language, outputLabel, output, openComponents, storage);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == CONTINUE
                ? new Result(language.getValue(),
                        new OutputPreferences(output.getValue(), ""), openComponents.isSelected())
                : null);
        return dialog.showAndWait();
    }

    public record Result(
            UiLanguage uiLanguage,
            OutputPreferences outputPreferences,
            boolean openComponents
    ) {
    }
}
