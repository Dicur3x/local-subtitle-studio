package io.github.dicur3x.lss.models;

import java.net.URI;

/** Curated official Qwen GGUF files with different local quality/resource trade-offs. */
public enum TranslationModelProfile {
    FAST(
            "fast",
            "Fast",
            "Qwen3 0.6B Q8_0",
            "Smallest and fastest option. Suitable for simple dialogue, but more likely to miss idioms, names, and long context.",
            "Qwen/Qwen3-0.6B-GGUF",
            "Qwen3-0.6B-Q8_0.gguf",
            639_446_688L,
            "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031"
    ),
    BALANCED(
            "balanced",
            "Balanced",
            "Qwen3 1.7B Q8_0",
            "A faster compromise with better context than the fast model. Its grammar still needs careful review.",
            "Qwen/Qwen3-1.7B-GGUF",
            "Qwen3-1.7B-Q8_0.gguf",
            1_834_426_016L,
            "061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a"
    ),
    MAXIMUM_QUALITY(
            "maximum-quality",
            "Maximum quality (recommended)",
            "Qwen3 4B Q4_K_M",
            "The recommended profile for natural subtitle drafts. It is slower and needs more memory and disk space.",
            "Qwen/Qwen3-4B-GGUF",
            "Qwen3-4B-Q4_K_M.gguf",
            2_497_280_256L,
            "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5"
    );

    private final String id;
    private final String displayName;
    private final String modelName;
    private final String description;
    private final String repository;
    private final String fileName;
    private final long sizeBytes;
    private final String sha256;

    TranslationModelProfile(
            String id,
            String displayName,
            String modelName,
            String description,
            String repository,
            String fileName,
            long sizeBytes,
            String sha256
    ) {
        this.id = id;
        this.displayName = displayName;
        this.modelName = modelName;
        this.description = description;
        this.repository = repository;
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
        return URI.create("https://huggingface.co/" + repository + "/resolve/main/"
                + fileName + "?download=true");
    }

    public URI sourceUri() {
        return URI.create("https://huggingface.co/" + repository);
    }

    public static TranslationModelProfile fromId(String id) {
        for (TranslationModelProfile profile : values()) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("Unknown translation model profile: " + id);
    }
}
