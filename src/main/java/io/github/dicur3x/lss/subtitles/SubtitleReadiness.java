package io.github.dicur3x.lss.subtitles;

import java.util.List;

public record SubtitleReadiness(boolean ready, List<String> problems) {
    public SubtitleReadiness {
        problems = problems == null ? List.of() : List.copyOf(problems);
        ready = problems.isEmpty();
    }

    public static SubtitleReadiness readyState() {
        return new SubtitleReadiness(true, List.of());
    }
}
