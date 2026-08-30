package io.github.dicur3x.lss.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationModelProfileTest {
    @Test
    void everyProfileUsesAnImmutableOfficialQwenArtifact() {
        for (TranslationModelProfile profile : TranslationModelProfile.values()) {
            assertEquals("https", profile.downloadUri().getScheme());
            assertEquals("huggingface.co", profile.downloadUri().getHost());
            assertTrue(profile.downloadUri().getPath().startsWith("/Qwen/"));
            assertTrue(profile.fileName().endsWith(".gguf"));
            assertTrue(profile.sizeBytes() > 500L * 1024 * 1024);
            assertTrue(profile.sha256().matches("[0-9a-f]{64}"));
            assertEquals(profile, TranslationModelProfile.fromId(profile.id()));
        }
    }

    @Test
    void balancedProfileIsTheRecommendedDefault() {
        assertTrue(Arrays.stream(TranslationModelProfile.values())
                .anyMatch(profile -> profile == TranslationModelProfile.BALANCED
                        && profile.displayName().contains("recommended")));
    }
}
