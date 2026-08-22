package io.github.dicur3x.lss.components;

public enum ManagedComponent {
    FFMPEG("ffmpeg", "FFmpeg"),
    WHISPER_CPP("whisper-cpp", "whisper.cpp");

    private final String id;
    private final String displayName;

    ManagedComponent(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
