# ADR 0001: MVP foundation

- Status: accepted for MVP 1
- Date: 2026-08-22

## Decision

Use Java 21, JavaFX 21 LTS, Gradle with the Kotlin DSL, and a single application module for the first working vertical slice.

Integrate command-line tools through `ProcessBuilder`. Parse structured `ffprobe` JSON into application-owned records; never expose Jackson or process-output details to the UI.

Keep architectural boundaries as packages and small interfaces:

```text
ui → MediaProbe → ffprobe adapter → ExternalProcessRunner
          ↓
     application-owned media models
```

## Why single-module now

The first milestone has one executable and one deployment lifecycle. Gradle subprojects would add build and module-path complexity without isolating a separately reusable component. The interfaces are extraction seams: a package can move to a subproject later without changing its callers.

## Operational rules

- External processes receive an argument list, not a shell command string.
- Standard output and standard error are drained concurrently.
- Exit codes are checked and cancellation terminates the child process.
- Media inspection runs off the JavaFX application thread.
- Video files are read-only inputs.

## Deferred

- `ffmpeg` audio extraction.
- whisper.cpp discovery and model management.
- Subtitle timing, segmentation, validation, and SRT export.
- Translation and correction engines.
- Native packaging and bundled runtime/tool licensing.
- Physical Gradle modules, when a real independent lifecycle justifies them.
