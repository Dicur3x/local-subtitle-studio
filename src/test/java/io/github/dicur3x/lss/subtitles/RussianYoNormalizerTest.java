package io.github.dicur3x.lss.subtitles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RussianYoNormalizerTest {
    @Test
    void restoresUnambiguousCommonFormsAndPreservesCase() {
        assertEquals("Он пришёл, ушёл и нашёл ответ. ПРИШЁЛ!",
                RussianYoNormalizer.normalizeText("Он пришел, ушел и нашел ответ. ПРИШЕЛ!"));
    }

    @Test
    void leavesAmbiguousWordsUntouched() {
        assertEquals("Все уже здесь, но все не так.",
                RussianYoNormalizer.normalizeText("Все уже здесь, но все не так."));
    }
}
