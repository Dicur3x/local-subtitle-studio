package io.github.dicur3x.lss.subtitles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
