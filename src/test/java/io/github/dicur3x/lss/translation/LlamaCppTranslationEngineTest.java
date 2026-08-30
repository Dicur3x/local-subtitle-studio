package io.github.dicur3x.lss.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.infrastructure.process.ExternalProcessRunner;
import io.github.dicur3x.lss.infrastructure.process.ProcessResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlamaCppTranslationEngineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void invokesStructuredLocalTranslationAndParsesStableIds() throws Exception {
        Path model = Files.createFile(temporaryDirectory.resolve("translation.gguf"));
        List<String> captured = new ArrayList<>();
        List<Path> schemaFiles = new ArrayList<>();
        ExternalProcessRunner runner = (command, cancellationRequested) -> {
            captured.addAll(command);
            Path schemaFile = Path.of(command.get(command.indexOf("--json-schema-file") + 1));
            schemaFiles.add(schemaFile);
            String schema = Files.readString(schemaFile);
            assertTrue(schema.contains("\"translations\""));
            assertTrue(schema.contains("\"enum\":[2]"));
            String prompt = command.get(command.indexOf("--prompt") + 1);
            assertTrue(prompt.contains("from en to ru"));
            assertTrue(prompt.contains("ID: 1"));
            assertTrue(prompt.contains("TRANSLATE: no"));
            assertTrue(prompt.contains("Earlier ″context″"));
            assertTrue(prompt.contains("ID: 2"));
            assertTrue(prompt.contains("TRANSLATE: yes"));
            return new ProcessResult(0,
                    "```json\n{\"translations\":[{\"id\":2,\"text\":\"Привет!\"}]}\n```", "");
        };
        TranslationBatch batch = new TranslationBatch("EN", "RU", List.of(
                new TranslationCueInput(1, "Earlier \"context\"", false),
                new TranslationCueInput(2, "Hello!", true),
                new TranslationCueInput(3, "Later context", false)));

        TranslationBatchResult result = new LlamaCppTranslationEngine(
                "llama-cli", model, runner, new ObjectMapper()).translate(batch, () -> false);

        assertEquals(List.of(new TranslatedText(2, "Привет!")), result.translations());
        assertEquals("llama-cli", captured.getFirst());
        assertTrue(captured.contains("--json-schema-file"));
        assertTrue(captured.contains("--no-jinja"));
        assertTrue(captured.contains("--prompt"));
        assertTrue(schemaFiles.stream().allMatch(Files::notExists));
    }

    @Test
    void reportsProcessAndResponseFailures() throws Exception {
        Path model = Files.createFile(temporaryDirectory.resolve("translation.gguf"));
        TranslationBatch batch = new TranslationBatch("en", "ru", List.of(
                new TranslationCueInput(1, "Hello", true)));
        ExternalProcessRunner failed = (command, cancellationRequested) ->
                new ProcessResult(7, "", "model could not be loaded");
        TranslationException processFailure = assertThrows(TranslationException.class,
                () -> new LlamaCppTranslationEngine(
                        "llama-cli", model, failed, new ObjectMapper()).translate(batch, () -> false));
        assertTrue(processFailure.getMessage().contains("model could not be loaded"));

        ExternalProcessRunner malformed = (command, cancellationRequested) ->
                new ProcessResult(0, "not json", "");
        assertThrows(TranslationException.class,
                () -> new LlamaCppTranslationEngine(
                        "llama-cli", model, malformed, new ObjectMapper()).translate(batch, () -> false));
    }

    @Test
    void requiresAnExistingModelBeforeStartingTheProcess() {
        ExternalProcessRunner runner = (command, cancellationRequested) -> {
            throw new AssertionError("Process must not start without a model");
        };
        TranslationBatch batch = new TranslationBatch("en", "ru", List.of(
                new TranslationCueInput(1, "Hello", true)));

        assertThrows(TranslationException.class,
                () -> new LlamaCppTranslationEngine(
                        "llama-cli", temporaryDirectory.resolve("missing.gguf"),
                        runner, new ObjectMapper()).translate(batch, () -> false));
    }
}
