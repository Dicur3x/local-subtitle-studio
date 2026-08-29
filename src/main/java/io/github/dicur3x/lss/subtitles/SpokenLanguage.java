package io.github.dicur3x.lss.subtitles;

import java.util.Arrays;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Languages supported by the multilingual Whisper vocabulary used by whisper.cpp 1.9.2. */
public record SpokenLanguage(String code, String displayName) {
    private static final String SUPPORTED_CODES = """
            en zh de es ru ko fr ja pt tr pl ca nl ar sv it id hi fi vi he uk el ms cs ro da hu ta no
            th ur hr bg lt la mi ml cy sk te fa lv bn sr az sl kn et mk br eu is hy ne mn bs kk sq sw
            gl mr pa si km sn yo so af oc ka be tg sd gu am yi lo uz fo ht ps tk nn mt sa lb my bo tl mg
            as tt haw ln ha ba jw su yue
            """;
    private static final List<String> POPULAR_ORDER = List.of(
            "en", "ru", "zh", "es", "fr", "de", "pt", "ar", "hi", "ja", "ko", "tr",
            "it", "pl", "uk", "nl", "id", "vi", "th", "sv", "cs", "ro", "el", "he", "fa"
    );

    public static final SpokenLanguage AUTO = new SpokenLanguage("auto", "Auto detect (recommended)");
    private static final List<SpokenLanguage> SUPPORTED = createSupported();

    public SpokenLanguage {
        code = Objects.requireNonNull(code, "code").strip().toLowerCase(Locale.ROOT);
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        if (code.isEmpty() || displayName.isEmpty()) {
            throw new IllegalArgumentException("Language code and name must not be blank");
        }
    }

    public static List<SpokenLanguage> choices() {
        return SUPPORTED;
    }

    public static List<SpokenLanguage> choices(Locale displayLocale) {
        Locale safeLocale = Objects.requireNonNullElse(displayLocale, Locale.ENGLISH);
        String autoName = "ru".equals(safeLocale.getLanguage())
                ? "Определить автоматически (рекомендуется)" : AUTO.displayName;
        Collator collator = Collator.getInstance(safeLocale);
        collator.setStrength(Collator.PRIMARY);
        Comparator<SpokenLanguage> ordering = Comparator
                .comparingInt((SpokenLanguage language) -> {
                    int position = POPULAR_ORDER.indexOf(language.code());
                    return position < 0 ? Integer.MAX_VALUE : position;
                })
                .thenComparing(SpokenLanguage::displayName, collator);
        List<SpokenLanguage> languages = Arrays.stream(SUPPORTED_CODES.split("\\s+"))
                .filter(code -> !code.isBlank())
                .map(code -> fromCode(code, safeLocale))
                .sorted(ordering)
                .toList();
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of(new SpokenLanguage(AUTO.code, autoName)), languages.stream())
                .toList();
    }

    public static String requireSupportedCode(String code) {
        String normalized = code == null || code.isBlank()
                ? AUTO.code : code.strip().toLowerCase(Locale.ROOT);
        if (SUPPORTED.stream().noneMatch(language -> language.code.equals(normalized))) {
            throw new IllegalArgumentException("Unsupported spoken-language code: " + normalized);
        }
        return normalized;
    }

    private static List<SpokenLanguage> createSupported() {
        List<SpokenLanguage> languages = Arrays.stream(SUPPORTED_CODES.split("\\s+"))
                .filter(code -> !code.isBlank())
                .map(SpokenLanguage::fromCode)
                .toList();
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(AUTO), languages.stream()).toList();
    }

    private static SpokenLanguage fromCode(String code) {
        return fromCode(code, Locale.ENGLISH);
    }

    private static SpokenLanguage fromCode(String code, Locale displayLocale) {
        Locale locale = Locale.forLanguageTag(code);
        String name = locale.getDisplayLanguage(displayLocale);
        if (name == null || name.isBlank() || name.equalsIgnoreCase(code)) {
            name = code.toUpperCase(Locale.ROOT);
        }
        return new SpokenLanguage(code, name);
    }

    @Override
    public String toString() {
        return code.equals("auto") ? displayName : displayName + " (" + code + ")";
    }
}
