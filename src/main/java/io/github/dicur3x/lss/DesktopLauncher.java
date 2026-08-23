package io.github.dicur3x.lss;

/** Plain Java entry point used by non-modular native packages. */
public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static void main(String[] arguments) {
        LocalSubtitleStudioApplication.main(arguments);
    }
}
