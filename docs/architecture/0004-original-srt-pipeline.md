# ADR 0004: Original-language SRT pipeline and cue timing

- Status: accepted for MVP 1
- Date: 2026-08-22

## Decision

Create an original-language SRT through one user action after a video and audio track are selected. The application:

1. decodes the selected stream to temporary 16 kHz mono PCM;
2. invokes the configured `whisper-cli` with automatic language detection, Silero VAD, word splitting, and full JSON output;
3. derives speech bounds from usable token offsets, falling back to segment offsets;
4. adds a 50 ms lead and a 200 ms tail, preserves an 800 ms minimum display time when the next phrase permits it, and leaves 100 ms before the next detected speech;
5. writes a UTF-8 SRT beside the video without replacing an existing file;
6. removes the temporary audio and transcription JSON.

The model, VAD, and executable are validated before decoding begins. Recognition and FFmpeg both use the shared cancellable external-process boundary and never invoke a shell.

## Timing rationale

A subtitle must not remain visible only because another subtitle begins much later. Its normal end is therefore based on the last recognized token plus a small reading tail, not the next segment's start. The following speech boundary is a maximum limit rather than the source of the end timestamp.

`--max-len 84` and word-aware splitting keep generated cues suitable for at most two display lines. The SRT writer balances text around 42 characters; later editing and validation milestones may split difficult long utterances more aggressively.

## Output policy

The detected language becomes part of the filename, for example `film.ru.srt`. If that path exists, the writer tries `film.ru.2.srt` and subsequent numbers. Empty recognition results are reported to the user and do not create empty subtitle files.

## Consequences

- The application now has a complete experimental original-subtitle vertical slice.
- Recognition remains fully local after components and models have been downloaded.
- Accuracy and speed depend on the chosen model and computer.
- The current MVP does not yet provide an editor, translation, speaker labels, or human review workflow.
