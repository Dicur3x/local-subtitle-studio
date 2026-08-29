package io.github.dicur3x.lss.ui;

import io.github.dicur3x.lss.settings.UiLanguage;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public final class I18n {
    private static final String BUNDLE = "io.github.dicur3x.lss.messages";
    private static volatile Locale locale = Locale.ENGLISH;

    private I18n() {
    }

    public static void use(UiLanguage language) {
        locale = Objects.requireNonNullElse(language, UiLanguage.ENGLISH).locale();
        Locale.setDefault(locale);
    }

    public static Locale locale() {
        return locale;
    }

    public static String tr(String key, Object... arguments) {
        String pattern = ResourceBundle.getBundle(BUNDLE, locale).getString(key);
        return arguments.length == 0 ? pattern : new MessageFormat(pattern, locale).format(arguments);
    }
}
