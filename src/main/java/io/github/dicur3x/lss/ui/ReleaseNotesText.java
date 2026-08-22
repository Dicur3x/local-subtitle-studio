package io.github.dicur3x.lss.ui;

import java.util.regex.Pattern;

final class ReleaseNotesText {
    private static final int MAXIMUM_CHARACTERS = 60_000;
    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)]\\([^)]+\\)");

    private ReleaseNotesText() {
    }

    static String toPlainText(String value) {
        String text = value == null ? "" : value.replace("\r\n", "\n").strip();
        text = HTML_COMMENT.matcher(text).replaceAll("");
        text = MARKDOWN_LINK.matcher(text).replaceAll("$1");
        text = text.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[*-]\\s+", "• ")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        if (text.length() <= MAXIMUM_CHARACTERS) {
            return text;
        }
        return text.substring(0, MAXIMUM_CHARACTERS).stripTrailing()
                + "\n\n…The remaining notes are available on the official source.";
    }
}
