package io.github.dicur3x.lss.media.ffprobe;

public final class FfprobeExecutableLocator {
    private static final String SYSTEM_PROPERTY = "lss.ffprobe.path";
    private static final String ENVIRONMENT_VARIABLE = "LSS_FFPROBE_PATH";

    private FfprobeExecutableLocator() {
    }

    public static String locate() {
        String propertyValue = System.getProperty(SYSTEM_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.strip();
        }

        String environmentValue = System.getenv(ENVIRONMENT_VARIABLE);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.strip();
        }

        return "ffprobe";
    }
}
