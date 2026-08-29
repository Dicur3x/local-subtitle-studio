package io.github.dicur3x.lss.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseNotesTextTest {
    @Test
    void convertsCommonGithubMarkdownToReadablePlainText() {
        String markdown = """
                ## What's Changed
                * **Fix VAD timestamps** by [@author](https://example.test/author)
                * Keep `--vad` enabled
                """;

        assertEquals("""
                What's Changed
                • Fix VAD timestamps by @author
                • Keep --vad enabled""", ReleaseNotesText.toPlainText(markdown));
    }
}
