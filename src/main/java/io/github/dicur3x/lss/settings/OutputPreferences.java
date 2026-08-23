package io.github.dicur3x.lss.settings;

public record OutputPreferences(OutputLocation location, String customDirectory) {
    public OutputPreferences {
        location = location == null ? OutputLocation.BESIDE_VIDEO : location;
        customDirectory = customDirectory == null ? "" : customDirectory.strip();
    }

    public static OutputPreferences defaults() {
        return new OutputPreferences(OutputLocation.BESIDE_VIDEO, "");
    }
}
