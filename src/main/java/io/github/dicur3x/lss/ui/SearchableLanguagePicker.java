package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.subtitles.SpokenLanguage;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

final class SearchableLanguagePicker {
    private final ComboBox<SpokenLanguage> control = new ComboBox<>();
    private final List<SpokenLanguage> choices;
    private final SpokenLanguage separator;
    private final int promotedCount;
    private SpokenLanguage selected;
    private boolean updating;
    private Consumer<SpokenLanguage> selectionListener = ignored -> { };

    SearchableLanguagePicker(
            List<SpokenLanguage> choices,
            SpokenLanguage initial,
            String separatorLabel,
            String promptText,
            int promotedCount
    ) {
        this.choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
        if (this.choices.isEmpty()) {
            throw new IllegalArgumentException("Language choices must not be empty");
        }
        selected = this.choices.contains(initial) ? initial : this.choices.getFirst();
        separator = new SpokenLanguage("separator", separatorLabel);
        this.promotedCount = Math.max(0, Math.min(promotedCount, this.choices.size()));
        configure(promptText);
    }

    ComboBox<SpokenLanguage> control() {
        return control;
    }

    SpokenLanguage selected() {
        return selected;
    }

    void setSelected(SpokenLanguage language) {
        if (language != null && choices.contains(language)) {
            selected = language;
            restoreSelection();
        }
    }

    void setOnSelection(Consumer<SpokenLanguage> listener) {
        selectionListener = Objects.requireNonNull(listener, "listener");
    }

    private void configure(String promptText) {
        control.setItems(FXCollections.observableArrayList(choicesWithSeparator()));
        control.setCellFactory(listView -> languageCell());
        control.setConverter(new StringConverter<>() {
            @Override
            public String toString(SpokenLanguage language) {
                return language == null || separator.equals(language) ? "" : language.toString();
            }

            @Override
            public SpokenLanguage fromString(String value) {
                return matchingLanguages(choices, value, I18n.locale()).stream()
                        .findFirst().orElse(selected);
            }
        });
        control.setEditable(true);
        control.setVisibleRowCount(14);
        control.setValue(selected);
        control.setMaxWidth(Double.MAX_VALUE);
        control.getEditor().setPromptText(promptText);
        control.getEditor().textProperty().addListener((observable, oldText, newText) -> {
            if (!updating) {
                filter(newText);
            }
        });
        control.getEditor().focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (focused) {
                Platform.runLater(control.getEditor()::selectAll);
            } else if (!updating) {
                restoreSelection();
            }
        });
        control.setOnShowing(event -> {
            if (!updating && control.getEditor().getText().equals(selected.toString())) {
                restoreSelection();
            }
        });
        control.setOnAction(event -> {
            if (updating) {
                return;
            }
            SpokenLanguage chosen = control.getValue();
            if (chosen != null && !separator.equals(chosen)) {
                selected = chosen;
                selectionListener.accept(chosen);
            }
            restoreSelection();
        });
    }

    private ListCell<SpokenLanguage> languageCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SpokenLanguage item, boolean empty) {
                super.updateItem(item, empty);
                boolean isSeparator = !empty && separator.equals(item);
                getStyleClass().remove("language-separator");
                if (isSeparator) {
                    getStyleClass().add("language-separator");
                }
                setDisable(isSeparator);
                setMouseTransparent(isSeparator);
                setText(empty || item == null ? null
                        : isSeparator ? "────────  " + item.displayName() : item.toString());
            }
        };
    }

    private List<SpokenLanguage> choicesWithSeparator() {
        List<SpokenLanguage> displayed = new ArrayList<>(choices.size() + 1);
        displayed.addAll(choices.subList(0, promotedCount));
        if (choices.size() > promotedCount) {
            displayed.add(separator);
            displayed.addAll(choices.subList(promotedCount, choices.size()));
        }
        return displayed;
    }

    private void filter(String query) {
        SpokenLanguage currentValue = control.getValue();
        if (query != null && (query.equals(selected.toString())
                || currentValue != null && !separator.equals(currentValue)
                && query.equals(currentValue.toString()))) {
            return;
        }
        String normalized = query == null ? "" : query.strip().toLowerCase(I18n.locale());
        List<SpokenLanguage> filtered = normalized.isEmpty()
                ? choicesWithSeparator() : matchingLanguages(choices, normalized, I18n.locale());
        updating = true;
        control.getItems().setAll(filtered);
        control.setValue(null);
        control.getEditor().setText(query == null ? "" : query);
        control.getEditor().positionCaret(control.getEditor().getText().length());
        updating = false;
        if (control.getEditor().isFocused()) {
            showResults();
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

    private void showResults() {
        Platform.runLater(() -> {
            control.show();
            control.getEditor().requestFocus();
            control.getEditor().positionCaret(control.getEditor().getText().length());
        });
    }

    private void restoreSelection() {
        updating = true;
        control.getItems().setAll(choicesWithSeparator());
        control.setValue(selected);
        control.getEditor().setText(selected.toString());
        updating = false;
    }
}
