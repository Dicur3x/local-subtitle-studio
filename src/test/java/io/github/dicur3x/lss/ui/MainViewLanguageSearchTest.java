package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.subtitles.SpokenLanguage;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainViewLanguageSearchTest {
    @Test
    void filtersRussianLanguageNamesInsideTheComboBox() {
        Locale russian = Locale.forLanguageTag("ru");

        var matches = MainView.matchingLanguages(
                SpokenLanguage.choices(russian), "китай", russian);

        assertEquals(1, matches.size());
        assertEquals("zh", matches.getFirst().code());
    }

    @Test
    void filtersByWhisperLanguageCode() {
        Locale english = Locale.ENGLISH;

        var matches = MainView.matchingLanguages(
                SpokenLanguage.choices(english), "yue", english);

        assertEquals(1, matches.size());
        assertEquals("yue", matches.getFirst().code());
    }
}
