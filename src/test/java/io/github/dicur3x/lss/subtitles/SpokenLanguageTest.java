package io.github.dicur3x.lss.subtitles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

class SpokenLanguageTest {
    @Test
    void exposesAutoAndEveryWhisperCppLanguage() {
        assertEquals(101, SpokenLanguage.choices().size());
        assertEquals("auto", SpokenLanguage.choices().getFirst().code());
        assertTrue(SpokenLanguage.choices().stream().anyMatch(language -> language.code().equals("ru")));
        assertTrue(SpokenLanguage.choices().stream().anyMatch(language -> language.code().equals("yue")));
    }

    @Test
    void rejectsUnknownLanguageCode() {
        assertThrows(IllegalArgumentException.class, () -> SpokenLanguage.requireSupportedCode("invalid"));
    }

    @Test
    void putsEnglishAndRussianBeforeOtherLanguagesInLocalizedChoices() {
        var choices = SpokenLanguage.choices(Locale.forLanguageTag("ru"));

        assertEquals("auto", choices.get(0).code());
        assertEquals("en", choices.get(1).code());
        assertEquals("ru", choices.get(2).code());
        assertEquals("zh", choices.get(3).code());
    }
}
