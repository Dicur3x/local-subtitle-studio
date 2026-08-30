package io.github.dicur3x.lss.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.dicur3x.lss.components.ComponentException;
import io.github.dicur3x.lss.components.DownloadClient;
import io.github.dicur3x.lss.components.DownloadResult;
import io.github.dicur3x.lss.components.OperationProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationModelManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void neverActivatesAModelWhoseSizeOrChecksumDoesNotMatch() throws Exception {
        Path models = temporaryDirectory.resolve("translation");
        TranslationModelManager manager = new TranslationModelManager(
                models, new WrongArtifactClient(), new ObjectMapper());

        assertThrows(ComponentException.class, () -> manager.install(
                TranslationModelProfile.FAST, (phase, done, total) -> { }, () -> false));

        assertTrue(manager.current().isEmpty());
        assertTrue(Files.notExists(models.resolve("current.json")));
    }

    private static final class WrongArtifactClient implements DownloadClient {
        @Override
        public String getText(URI uri, long maximumBytes, BooleanSupplier cancellationRequested) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DownloadResult download(
                URI uri,
                Path destination,
                long maximumBytes,
                String phase,
                OperationProgress progress,
                BooleanSupplier cancellationRequested
        ) throws IOException {
            byte[] wrong = new byte[]{1, 2, 3};
            Files.write(destination, wrong);
            try {
                String sha = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(wrong));
                return new DownloadResult(wrong.length, sha);
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
