package io.github.dicur3x.lss.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagedToolsServiceTest {
    @Test
    void comparesFfmpegBuildSuffixByItsReleaseVersion() {
        assertEquals("9.0.1", ManagedToolsService.normalizeVersion(
                "9.0.1-essentials_build-www.gyan.dev"));
        assertEquals("9.0.1", ManagedToolsService.normalizeVersion("v9.0.1"));
    }
}
