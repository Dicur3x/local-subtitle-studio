# ADR 0004: Original-language SRT pipeline and cue timing

- Status: accepted for MVP 1
- Date: 2026-08-22

## Decision

Create an original-language SRT through one user action after a video and audio track are selected. The application:

1. decodes the selected stream to temporary 16 kHz mono PCM;
2. divides feature-length PCM into eight-minute ownership windows with five-second overlaps, runs a fresh `whisper-cli` process for each window with Silero VAD, word splitting, full JSON output, and a bounded text context, then merges only the owned overlap regions back onto the original timeline;
3. derives speech bounds from usable token offsets, falling back to segment offsets;
4. splits long utterances at token boundaries and adds a 50 ms lead and a 200 ms tail, preserves an 800 ms minimum display time when the next phrase permits it, caps a cue at seven seconds by default so a short phrase cannot linger through silence, and leaves 100 ms before the next detected speech;
5. retries a suspiciously repeated recognition window with text-context carry disabled and refuses to write an SRT if the retry remains stuck;
6. conservatively restores only unambiguous Russian `ё` forms, then validates cue ordering, overlaps, configured line limits, reading speed, adjacent repeated text, and token confidence;
7. writes a UTF-8 SRT beside the video without replacing an existing file and offers an in-app editor for the cues flagged by validation;
8. removes the temporary audio, chunk WAVs, and transcription JSON.

The model, VAD, and executable are validated before decoding begins. Recognition and FFmpeg both use the shared cancellable external-process boundary and never invoke a shell. Chunk progress is aggregated monotonically, including an automatic retry.

## Timing rationale

A subtitle must not remain visible only because another subtitle begins much later. Its normal end is therefore based on the last recognized token plus a small reading tail, not the next segment's start. The following speech boundary is a maximum limit rather than the source of the end timestamp.

`--max-len 84` provides an initial hint to whisper.cpp. The application also owns the final segmentation step, preserves token boundaries when splitting, and balances the configured number of lines. Readability and timing defaults can be adjusted in Advanced settings without changing component paths.

The UI maps the engine's 0–100 recognition progress into an overall five-stage bar: audio preparation, recognition, timing, validation, and SRT writing. Operations that do not expose a meaningful total remain indeterminate instead of presenting a fabricated percentage.

## Output policy

The detected language becomes part of the filename, for example `film.ru.srt`. If that path exists, the writer tries `film.ru.2.srt` and subsequent numbers. Empty recognition results are reported to the user and do not create empty subtitle files.

## Consequences

- The application now has a complete experimental original-subtitle vertical slice.
- Long cues and structural timing errors are handled before the file is written; non-fatal readability warnings link to a focused review editor after completion.
- Recognition remains fully local after components and models have been downloaded.
- Accuracy and speed depend on the chosen model and computer.
- The current MVP provides text-only review of flagged cues, but not audio scrubbing, translation, or speaker labels.
