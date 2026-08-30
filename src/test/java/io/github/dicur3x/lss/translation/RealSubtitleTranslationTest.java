package io.github.dicur3x.lss.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.DefaultExternalProcessRunner;
import io.github.dicur3x.lss.subtitles.SubtitleCue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("manual")
class RealSubtitleTranslationTest {
    private static final int SAMPLE_SIZE = 12;

    @Test
    void translatesAContextualWindowFromARealSrtWithoutChangingTiming() throws Exception {
        Path executable = configuredPath("lss.real.llama");
        Path model = configuredPath("lss.real.translation.model");
        Path srt = configuredPath("lss.real.translation.srt");
        assumeTrue(Files.isRegularFile(executable), "Set -PrealLlama to llama-cli.exe");
        assumeTrue(Files.isRegularFile(model), "Set -PrealTranslationModel to a GGUF model");
        assumeTrue(Files.isRegularFile(srt), "Set -PrealTranslationSrt to a UTF-8 SRT file");

        List<SubtitleCue> allCues = readSrt(srt);
        int requestedStart = Integer.getInteger("lss.real.translation.start.cue", 0);
        int start = Math.max(0, Math.min(requestedStart, allCues.size() - SAMPLE_SIZE));
        List<SubtitleCue> source = List.copyOf(allCues.subList(start,
                Math.min(start + SAMPLE_SIZE, allCues.size())));
        assumeTrue(source.size() == SAMPLE_SIZE,
                "The SRT needs at least 12 cues; parsed " + allCues.size());

        int batchSize = Integer.getInteger(
                "lss.real.translation.batch.size", SubtitleTranslationService.DEFAULT_BATCH_SIZE);
        SubtitleTranslationService service = new SubtitleTranslationService(
                new LlamaCppTranslationEngine(executable.toString(), model,
                        new DefaultExternalProcessRunner(), new ObjectMapper()),
                batchSize, SubtitleTranslationService.DEFAULT_CONTEXT_CUES);
        TranslatedSubtitles translated = service.translate(
                source, "Russian", "English", () -> false, percent -> { });

        assertEquals(source.size(), translated.translatedCues().size());
        for (int index = 0; index < source.size(); index++) {
            SubtitleCue original = source.get(index);
            SubtitleCue result = translated.translatedCues().get(index);
            assertEquals(original.id(), result.id());
            assertEquals(original.start(), result.start());
            assertEquals(original.end(), result.end());
            assertTrue(result.originalText().matches(".*[A-Za-z].*"));
            System.out.println(original.id() + " | " + original.originalText().replace('\n', ' ')
                    + " -> " + result.originalText().replace('\n', ' '));
        }
    }

    @Test
    void translatesOneRealCueWithoutCopyingItsContext() throws Exception {
        Path executable = configuredPath("lss.real.llama");
        Path model = configuredPath("lss.real.translation.model");
        Path srt = configuredPath("lss.real.translation.srt");
        assumeTrue(Files.isRegularFile(executable), "Set -PrealLlama to llama-cli.exe");
        assumeTrue(Files.isRegularFile(model), "Set -PrealTranslationModel to a GGUF model");
        assumeTrue(Files.isRegularFile(srt), "Set -PrealTranslationSrt to a UTF-8 SRT file");

        List<SubtitleCue> cues = readSrt(srt);
        int requestedStart = Integer.getInteger("lss.real.translation.start.cue", 0);
        int start = Math.max(0, Math.min(requestedStart, cues.size() - 3));
        TranslationBatch batch = new TranslationBatch("Russian", "English", List.of(
                input(cues.get(start), true),
                input(cues.get(start + 1), false),
                input(cues.get(start + 2), false)));
        TranslationBatchResult result = new LlamaCppTranslationEngine(
                executable.toString(), model, new DefaultExternalProcessRunner(), new ObjectMapper())
                .translate(batch, () -> false);

        assertEquals(1, result.translations().size());
        assertEquals(cues.get(start).id(), result.translations().getFirst().id());
        assertTrue(result.translations().getFirst().text().matches(".*[A-Za-z].*"));
        System.out.println(cues.get(start).originalText().replace('\n', ' ') + " -> "
                + result.translations().getFirst().text().replace('\n', ' '));
    }

    private static List<SubtitleCue> readSrt(Path path) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8)
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        List<SubtitleCue> cues = new ArrayList<>();
        String[] blocks = content.split("\\n[\\t ]*\\n");
        for (String block : blocks) {
            String[] lines = block.strip().split("\\n");
            if (lines.length < 3 || !lines[1].contains(" --> ")) {
                continue;
            }
            String[] timing = lines[1].split(" --> ", 2);
            String text = String.join("\n", java.util.Arrays.copyOfRange(lines, 2, lines.length));
            cues.add(new SubtitleCue(Long.parseLong(lines[0].strip()),
                    parseTime(timing[0]), parseTime(timing[1]), text, List.of()));
        }
        return cues;
    }

    private static Duration parseTime(String value) {
        String[] parts = value.strip().split("[:,]");
        return Duration.ofHours(Long.parseLong(parts[0]))
                .plusMinutes(Long.parseLong(parts[1]))
                .plusSeconds(Long.parseLong(parts[2]))
                .plusMillis(Long.parseLong(parts[3]));
    }

    private static TranslationCueInput input(SubtitleCue cue, boolean translate) {
        return new TranslationCueInput(cue.id(), cue.originalText(), translate);
    }

    private static Path configuredPath(String property) {
        String value = System.getProperty(property, "");
        return value.isBlank() ? Path.of("missing") : Path.of(value).toAbsolutePath().normalize();
    }
}
