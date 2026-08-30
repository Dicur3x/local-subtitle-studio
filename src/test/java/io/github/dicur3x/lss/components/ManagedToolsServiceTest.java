package io.github.dicur3x.lss.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagedToolsServiceTest {
    @Test
    void comparesFfmpegBuildSuffixByItsReleaseVersion() {
        assertEquals("9.0.1", ManagedToolsService.normalizeVersion(
                "9.0.1-essentials_build-www.gyan.dev"));
        assertEquals("9.0.1", ManagedToolsService.normalizeVersion("v9.0.1"));
        assertEquals("10621", ManagedToolsService.normalizeVersion("b10621"));
    }

    @Test
    void readsTheBuildNumberFromCurrentLlamaCliVersionOutput() {
        assertEquals("b10621", ManagedToolsService.llamaBuildVersion(
                "version: 0.3.0-dev (build 10621, commit c1d0e7a00)\n"
                        + "built with Clang 20.1.8 for Windows x86_64"));
    }
}
