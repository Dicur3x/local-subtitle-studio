# ADR 0006: Reconcile whisper.cpp VAD token timestamps with the original timeline

- Status: accepted
- Date: 2026-08-23

## Context

With VAD enabled in whisper.cpp 1.9.2, segment getters map timestamps to the original audio timeline. Raw `whisper_token_data.t0/t1` values can stay on the VAD-processed timeline where silence has been removed. The 1.9.2 CLI full-JSON writer emits those raw token fields.

Local Subtitle Studio originally used the first and last token as the speech boundary. On a real episode with opening silence, the first JSON segment was 2350–3830 ms while its raw tokens were 0–1480 ms. The generated SRT therefore started at 0 ms and accumulated an early shift as more silence was removed.

## Decision

- Segment offsets are authoritative speech boundaries.
- If token offsets fall outside the segment by more than 100 ms, treat them as a compressed VAD timebase.
- Project token positions linearly into the authoritative segment interval. This retains useful relative word positions for splitting without shifting the complete cue.
- If valid segment offsets are absent, fall back to token bounds.
- Keep a regression test based on the real 2350–3830 ms / 0–1480 ms case.

The default 50 ms start padding makes that first SRT cue begin at approximately 2300 ms rather than 0 ms.

## Sources

- [whisper.cpp 1.9.2 API timestamp contract](https://github.com/ggml-org/whisper.cpp/blob/v1.9.2/include/whisper.h)
- [whisper.cpp 1.9.2 CLI JSON writer](https://github.com/ggml-org/whisper.cpp/blob/v1.9.2/examples/cli/cli.cpp)
- [whisper.cpp 1.9.2 release notes](https://github.com/ggml-org/whisper.cpp/discussions/3971)

## Consequences

Previously generated SRT files are not repaired in place and must be regenerated. Segment-level timing is accurate to the original audio; token-level timing inside a VAD-compressed segment is an approximation until the CLI JSON writer uses the mapped token getters directly.
