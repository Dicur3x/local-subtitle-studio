package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.settings.UiLanguage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class I18nTest {
    @AfterEach
    void restoreEnglish() {
        I18n.use(UiLanguage.ENGLISH);
    }

    @Test
    void loadsRussianMessagesAndKeepsTheSameKeyCatalog() {
        ResourceBundle english = ResourceBundle.getBundle(
                "io.github.dicur3x.lss.messages", Locale.ENGLISH);
        ResourceBundle russian = ResourceBundle.getBundle(
                "io.github.dicur3x.lss.messages", Locale.forLanguageTag("ru"));

        assertEquals(english.keySet(), russian.keySet());
        I18n.use(UiLanguage.RUSSIAN);
        assertEquals("Компоненты", I18n.tr("main.components"));
        assertFalse(I18n.tr("main.heading").isBlank());
    }
}
