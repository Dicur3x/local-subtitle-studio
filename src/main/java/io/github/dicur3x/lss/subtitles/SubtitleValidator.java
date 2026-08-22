package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SubtitleValidator {
    private final SubtitlePreferences preferences;
    private final SubtitleTextFormatter formatter;

    public SubtitleValidator(SubtitlePreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        formatter = new SubtitleTextFormatter(preferences);
    }

    public SubtitleValidationReport validate(List<SubtitleCue> cues) throws SubtitleCreationException {
        List<SubtitleCue> safeCues = List.copyOf(Objects.requireNonNull(cues, "cues"));
        if (safeCues.isEmpty()) {
            throw new SubtitleCreationException("No subtitle cues were produced.");
        }

        int longLines = 0;
        int tooManyLines = 0;
        int tooFast = 0;
        int repeated = 0;
        SubtitleCue previous = null;
        for (int index = 0; index < safeCues.size(); index++) {
            SubtitleCue cue = safeCues.get(index);
            if (cue.id() != index + 1L) {
                throw new SubtitleCreationException("Subtitle cue numbering is invalid.");
            }
            if (previous != null && cue.start().compareTo(previous.end()) < 0) {
                throw new SubtitleCreationException("Subtitle cues overlap after timing optimization.");
            }

            String formatted = formatter.format(cue.originalText());
            String[] lines = formatted.split("\\R", -1);
            if (lines.length > preferences.maximumLines()) {
                tooManyLines++;
            }
            if (java.util.Arrays.stream(lines)
                    .anyMatch(line -> line.length() > preferences.maximumCharactersPerLine())) {
                longLines++;
            }
            double seconds = cue.end().minus(cue.start()).toMillis() / 1_000d;
            double charactersPerSecond = cue.originalText().length() / Math.max(0.001d, seconds);
            if (charactersPerSecond > preferences.maximumCharactersPerSecond()) {
                tooFast++;
            }
            if (previous != null && normalize(previous.originalText()).equals(normalize(cue.originalText()))) {
                repeated++;
            }
            previous = cue;
        }

        List<String> warnings = new ArrayList<>();
        addCountWarning(warnings, longLines, "cue(s) contain a line longer than the configured limit");
        addCountWarning(warnings, tooManyLines, "cue(s) exceed the configured line count");
        addCountWarning(warnings, tooFast, "cue(s) may be too fast to read");
        addCountWarning(warnings, repeated, "adjacent cue(s) repeat the same text");
        return new SubtitleValidationReport(warnings);
    }

    private static void addCountWarning(List<String> warnings, int count, String message) {
        if (count > 0) {
            warnings.add(count + " " + message + ".");
        }
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").strip().toLowerCase(java.util.Locale.ROOT);
    }
}
