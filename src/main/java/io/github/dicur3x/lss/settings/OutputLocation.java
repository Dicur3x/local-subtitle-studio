package io.github.dicur3x.lss.settings;

public enum OutputLocation {
    BESIDE_VIDEO("Beside the video"),
    SUBS_FOLDER("Subs folder beside the video"),
    CUSTOM_FOLDER("Chosen folder");

    private final String displayName;

    OutputLocation(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
