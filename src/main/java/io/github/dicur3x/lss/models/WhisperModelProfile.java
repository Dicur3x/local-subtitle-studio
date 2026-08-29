package io.github.dicur3x.lss.models;

import java.net.URI;

public enum WhisperModelProfile {
    FAST(
            "fast",
            "Fast",
            "small-q5_1",
            "Fastest practical multilingual option. Lower accuracy on noise, accents, and overlapping speech.",
            "ggml-small-q5_1.bin",
            190_085_487L,
            "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"
    ),
    BALANCED(
            "balanced",
            "Balanced (recommended)",
            "large-v3-turbo-q5_0",
            "Strong multilingual recognition with a much smaller download and faster decoding than full large-v3.",
            "ggml-large-v3-turbo-q5_0.bin",
            574_041_195L,
            "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2"
    ),
    MAXIMUM_QUALITY(
            "maximum-quality",
            "Maximum quality",
            "large-v3",
            "Highest-quality multilingual profile here, but it needs about 3.1 GB on disk and is much slower on CPU.",
            "ggml-large-v3.bin",
            3_095_033_483L,
            "64d182b440b98d5203c4f9bd541544d84c605196c4f7b845dfa11fb23594d1e2"
    );

    private static final String MODEL_BASE =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";

    private final String id;
    private final String displayName;
    private final String modelName;
    private final String description;
    private final String fileName;
    private final long sizeBytes;
    private final String sha256;

    WhisperModelProfile(
            String id,
            String displayName,
            String modelName,
            String description,
            String fileName,
            long sizeBytes,
            String sha256
    ) {
        this.id = id;
        this.displayName = displayName;
        this.modelName = modelName;
        this.description = description;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String modelName() {
        return modelName;
    }

    public String description() {
        return description;
    }

    public String fileName() {
        return fileName;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String sha256() {
        return sha256;
    }

    public URI downloadUri() {
        return URI.create(MODEL_BASE + fileName + "?download=true");
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static WhisperModelProfile fromId(String id) {
        for (WhisperModelProfile profile : values()) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown Whisper model profile: " + id);
    }
}
