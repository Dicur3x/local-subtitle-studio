package io.github.dicur3x.lss.subtitles;

import io.github.dicur3x.lss.settings.SubtitlePreferences;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Splits long recognition segments without discarding Whisper's token timestamps. */
public final class SubtitleSegmenter {
    private final int maximumCharactersPerCue;

    public SubtitleSegmenter(SubtitlePreferences preferences) {
        maximumCharactersPerCue = Objects.requireNonNull(
                preferences, "preferences").maximumCharactersPerCue();
    }

    public List<RecognizedSegment> segment(List<RecognizedSegment> recognized) {
        List<RecognizedSegment> result = new ArrayList<>();
        for (RecognizedSegment source : Objects.requireNonNull(recognized, "recognized")) {
            String normalized = normalize(source.text());
            if (normalized.length() <= maximumCharactersPerCue) {
                result.add(copyWithId(source, result.size() + 1L));
            } else if (!source.tokens().isEmpty()) {
                appendTokenChunks(source, result);
            } else {
                appendEstimatedChunks(source, result);
            }
        }
        return List.copyOf(result);
    }

    private void appendTokenChunks(RecognizedSegment source, List<RecognizedSegment> result) {
        List<TokenTiming> chunk = new ArrayList<>();
        for (TokenTiming token : source.tokens()) {
            List<TokenTiming> candidate = new ArrayList<>(chunk);
            candidate.add(token);
            if (!chunk.isEmpty() && tokenText(candidate).length() > maximumCharactersPerCue) {
                appendTokenChunk(chunk, result);
                chunk.clear();
            }
            chunk.add(token);
        }
        appendTokenChunk(chunk, result);
    }

    private static void appendTokenChunk(List<TokenTiming> tokens, List<RecognizedSegment> result) {
        if (tokens.isEmpty()) {
            return;
        }
        String text = tokenText(tokens);
        if (text.isEmpty()) {
            return;
        }
        TokenTiming first = tokens.getFirst();
        TokenTiming last = tokens.getLast();
        result.add(new RecognizedSegment(
                result.size() + 1L, first.start(), last.end(), text, List.copyOf(tokens)));
    }

    private void appendEstimatedChunks(RecognizedSegment source, List<RecognizedSegment> result) {
        List<String> chunks = wordChunks(source.text());
        long startMillis = source.speechStart().toMillis();
        long totalMillis = source.speechEnd().minus(source.speechStart()).toMillis();
        for (int index = 0; index < chunks.size(); index++) {
            long chunkStart = startMillis + Math.multiplyExact(totalMillis, index) / chunks.size();
            long chunkEnd = startMillis + Math.multiplyExact(totalMillis, index + 1L) / chunks.size();
            if (chunkEnd <= chunkStart) {
                chunkEnd = chunkStart + 1;
            }
            result.add(new RecognizedSegment(
                    result.size() + 1L,
                    Duration.ofMillis(chunkStart),
                    Duration.ofMillis(chunkEnd),
                    chunks.get(index),
                    List.of()));
        }
    }

    private List<String> wordChunks(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : normalize(text).split(" ")) {
            int candidateLength = current.isEmpty() ? word.length() : current.length() + 1 + word.length();
            if (!current.isEmpty() && candidateLength > maximumCharactersPerCue) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private static RecognizedSegment copyWithId(RecognizedSegment source, long id) {
        return new RecognizedSegment(
                id, source.speechStart(), source.speechEnd(), source.text(), source.tokens());
    }

    private static String tokenText(List<TokenTiming> tokens) {
        return normalize(tokens.stream().map(TokenTiming::text)
                .collect(java.util.stream.Collectors.joining()));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }
}
