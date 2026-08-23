package io.github.dicur3x.lss.settings;

import java.util.Locale;

public enum UiLanguage {
    ENGLISH("English", Locale.ENGLISH),
    RUSSIAN("Русский", Locale.forLanguageTag("ru"));

    private final String displayName;
    private final Locale locale;

    UiLanguage(String displayName, Locale locale) {
        this.displayName = displayName;
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
