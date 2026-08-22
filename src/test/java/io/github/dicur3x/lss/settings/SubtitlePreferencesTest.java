package io.github.dicur3x.lss.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubtitlePreferencesTest {
    @Test
    void replacesMissingOrInvalidJsonValuesWithSafeDefaults() {
        SubtitlePreferences preferences = new SubtitlePreferences(0, 0, 0, -1, -1, -1, 0);

        assertEquals(42, preferences.maximumCharactersPerLine());
        assertEquals(2, preferences.maximumLines());
        assertEquals(84, preferences.maximumCharactersPerCue());
        assertEquals(20d, preferences.maximumCharactersPerSecond());
    }
}
