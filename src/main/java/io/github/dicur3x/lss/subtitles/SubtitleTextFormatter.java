package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class SubtitleTextFormatter {
    private final int maximumLineLength;
    private final int maximumLines;

    SubtitleTextFormatter(SubtitlePreferences preferences) {
        SubtitlePreferences safe = Objects.requireNonNull(preferences, "preferences");
        maximumLineLength = safe.maximumCharactersPerLine();
        maximumLines = safe.maximumLines();
    }

    String format(String text) {
        String normalized = normalize(text);
        if (normalized.length() <= maximumLineLength) {
            return normalized;
        }
        List<String> words = new ArrayList<>(List.of(normalized.split(" ")));
        int desiredLines = Math.min(maximumLines,
                Math.max(1, (normalized.length() + maximumLineLength - 1) / maximumLineLength));
        desiredLines = Math.min(desiredLines, words.size());
        if (desiredLines <= 1) {
            return normalized;
        }

        List<String> lines = new ArrayList<>(desiredLines);
        int firstWord = 0;
        for (int line = 0; line < desiredLines - 1; line++) {
            int linesRemaining = desiredLines - line;
            int lastPossibleBreak = words.size() - (linesRemaining - 1);
            int remainingLength = joinedLength(words, firstWord, words.size());
            int targetLength = (remainingLength + linesRemaining - 1) / linesRemaining;
            int bestBreak = firstWord + 1;
            int bestScore = Integer.MAX_VALUE;
            for (int candidate = firstWord + 1; candidate <= lastPossibleBreak; candidate++) {
                int length = joinedLength(words, firstWord, candidate);
                int overflow = Math.max(0, length - maximumLineLength);
                int score = overflow * 10_000 + Math.abs(length - targetLength);
                if (score < bestScore) {
                    bestScore = score;
                    bestBreak = candidate;
                }
            }
            lines.add(String.join(" ", words.subList(firstWord, bestBreak)));
            firstWord = bestBreak;
        }
        lines.add(String.join(" ", words.subList(firstWord, words.size())));
        return String.join("\r\n", lines);
    }

    private static int joinedLength(List<String> words, int from, int to) {
        int length = 0;
        for (int index = from; index < to; index++) {
            length += words.get(index).length();
            if (index > from) {
                length++;
            }
        }
        return length;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }
}
