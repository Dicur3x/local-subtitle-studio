package io.github.dicur3x.lss.subtitles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineProgressTest {
    @Test
    void mapsRealWhisperProgressIntoTheOverallPipeline() {
        PipelineProgress halfway = PipelineProgress.at(
                PipelineStage.TRANSCRIBING, 50, "Recognizing");

        assertEquals(50, halfway.stagePercent());
        assertEquals(53, halfway.overallPercent());
    }

    @Test
    void parsesWhisperCppProgressLines() {
        assertEquals(65, WhisperCppTranscriber.progressFromLine(
                "whisper_print_progress_callback: progress =  65%").orElseThrow());
    }
}
