package io.github.dicur3x.lss.subtitles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Restores only common unambiguous Russian forms; ambiguous words such as "все" stay untouched. */
final class RussianYoNormalizer {
    private static final Map<Pattern, String> REPLACEMENTS = replacements();

    private RussianYoNormalizer() {
    }

    static List<RecognizedSegment> normalize(List<RecognizedSegment> segments, String language) {
        if (!"ru".equalsIgnoreCase(language)) {
            return segments;
        }
        return segments.stream().map(segment -> {
            List<TokenTiming> tokens = segment.tokens().stream()
                    .map(token -> new TokenTiming(normalizeText(token.text()), token.start(), token.end(),
                            token.probability()))
                    .toList();
            return new RecognizedSegment(
                    segment.id(), segment.speechStart(), segment.speechEnd(),
                    normalizeText(segment.text()), tokens);
        }).toList();
    }

    static String normalizeText(String source) {
        String result = source;
        for (Map.Entry<Pattern, String> entry : REPLACEMENTS.entrySet()) {
            Matcher matcher = entry.getKey().matcher(result);
            StringBuffer replaced = new StringBuffer();
            while (matcher.find()) {
                String replacement = matchCase(matcher.group(), entry.getValue());
                matcher.appendReplacement(replaced, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(replaced);
            result = replaced.toString();
        }
        return result;
    }

    private static Map<Pattern, String> replacements() {
        Map<Pattern, String> replacements = new LinkedHashMap<>();
        add(replacements, "пришел", "пришёл");
        add(replacements, "ушел", "ушёл");
        add(replacements, "пошел", "пошёл");
        add(replacements, "зашел", "зашёл");
        add(replacements, "вошел", "вошёл");
        add(replacements, "дошел", "дошёл");
        add(replacements, "нашел", "нашёл");
        add(replacements, "перешел", "перешёл");
        add(replacements, "произошел", "произошёл");
        add(replacements, "сошел", "сошёл");
        add(replacements, "подошел", "подошёл");
        add(replacements, "отошел", "отошёл");
        add(replacements, "обошел", "обошёл");
        add(replacements, "провел", "провёл");
        add(replacements, "привел", "привёл");
        add(replacements, "перевел", "перевёл");
        add(replacements, "повел", "повёл");
        add(replacements, "завел", "завёл");
        add(replacements, "подвел", "подвёл");
        add(replacements, "обрел", "обрёл");
        add(replacements, "приобрел", "приобрёл");
        return Map.copyOf(replacements);
    }

    private static void add(Map<Pattern, String> replacements, String source, String target) {
        replacements.put(Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(source)
                + "(?![\\p{L}\\p{N}_])"), target);
    }

    private static String matchCase(String matched, String replacement) {
        if (matched.equals(matched.toUpperCase(Locale.ROOT))) {
            return replacement.toUpperCase(Locale.ROOT);
        }
        if (!matched.isEmpty() && Character.isUpperCase(matched.codePointAt(0))) {
            int firstLength = Character.charCount(replacement.codePointAt(0));
            return replacement.substring(0, firstLength).toUpperCase(Locale.ROOT)
                    + replacement.substring(firstLength);
        }
        return replacement;
    }
}
