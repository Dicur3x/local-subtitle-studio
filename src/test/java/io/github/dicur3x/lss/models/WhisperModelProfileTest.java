package io.github.dicur3x.lss.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhisperModelProfileTest {
    @Test
    void everyProfileUsesImmutableHttpsArtifactMetadata() {
        for (WhisperModelProfile profile : WhisperModelProfile.values()) {
            assertEquals("https", profile.downloadUri().getScheme());
            assertTrue(profile.downloadUri().getHost().endsWith("huggingface.co"));
            assertTrue(profile.sizeBytes() > 0);
            assertTrue(profile.sha256().matches("[0-9a-f]{64}"));
            assertEquals(profile, WhisperModelProfile.fromId(profile.id()));
        }
    }

    @Test
    void balancedProfileIsTheRecommendedDefault() {
        assertTrue(Arrays.stream(WhisperModelProfile.values())
                .anyMatch(profile -> profile == WhisperModelProfile.BALANCED
                        && profile.displayName().contains("recommended")));
    }
}
