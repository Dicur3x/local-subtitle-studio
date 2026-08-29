package io.github.dicur3x.lss.subtitles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Applies only corrections that do not guess or rewrite recognized dialogue. */
public final class SubtitlePostProcessor {
    private static final Duration MAXIMUM_DUPLICATE_GAP = Duration.ofMillis(250);

    public List<SubtitleCue> process(List<SubtitleCue> source) {
        List<SubtitleCue> result = new ArrayList<>();
        for (SubtitleCue cue : Objects.requireNonNull(source, "source")) {
            if (!result.isEmpty() && isAdjacentDuplicate(result.getLast(), cue)) {
                SubtitleCue previous = result.removeLast();
                List<TokenTiming> tokens = new ArrayList<>(previous.tokens());
                tokens.addAll(cue.tokens());
                result.add(new SubtitleCue(
                        previous.id(), previous.start(), max(previous.end(), cue.end()),
                        previous.originalText(), tokens));
            } else {
                result.add(new SubtitleCue(
                        result.size() + 1L, cue.start(), cue.end(), cue.originalText(), cue.tokens()));
            }
        }
        for (int index = 0; index < result.size(); index++) {
            SubtitleCue cue = result.get(index);
            if (cue.id() != index + 1L) {
                result.set(index, new SubtitleCue(
                        index + 1L, cue.start(), cue.end(), cue.originalText(), cue.tokens()));
            }
        }
        return List.copyOf(result);
    }

    private static boolean isAdjacentDuplicate(SubtitleCue previous, SubtitleCue current) {
        Duration gap = current.start().minus(previous.end());
        return !gap.isNegative()
                && gap.compareTo(MAXIMUM_DUPLICATE_GAP) <= 0
                && normalize(previous.originalText()).equals(normalize(current.originalText()));
    }

    private static Duration max(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
}
