# Local Subtitle Studio

Local Subtitle Studio is a desktop application for creating subtitles from video while keeping media on the user's computer. The first supported platform is Windows.

> Status: early MVP development. The current build can accept a video with drag-and-drop, inspect it with `ffprobe`, and show its audio tracks.

## Current features

- Drag-and-drop or file picker for local video files.
- Asynchronous media inspection, so the UI remains responsive.
- Audio track details: language metadata, title, codec, channel layout, bitrate, and sample rate.
- Clear errors when `ffprobe` is missing or cannot read a file.
- Cancellation of the active inspection process.

No media is uploaded. Cloud services are not used by the current build.

## Requirements

- JDK 21 or newer.
- `ffprobe` available on `PATH`, or its path supplied through `LSS_FFPROBE_PATH`.
- Windows 10/11 for the currently tested build. The code avoids Windows-only process invocation so Linux and macOS can be added later.

## Run

```powershell
.\gradlew.bat run
```

To use a specific `ffprobe` executable for one PowerShell session:

```powershell
$env:LSS_FFPROBE_PATH = 'C:\Tools\ffmpeg\bin\ffprobe.exe'
.\gradlew.bat run
```

## Test

Unit tests:

```powershell
.\gradlew.bat test
```

End-to-end media probe test (requires both `ffmpeg` and `ffprobe`):

```powershell
.\gradlew.bat integrationTest
```

## Architecture

The MVP intentionally starts as one Gradle module. Packages and interfaces separate UI, media inspection, and external-process infrastructure. This keeps the first working vertical slice small without creating a monolith. See [`docs/architecture/0001-foundation.md`](docs/architecture/0001-foundation.md).

Planned pipeline:

```text
video → audio track → PCM audio → speech detection → transcription
      → timing/segmentation → validation → SRT
```

## Licensing

A source-code license has not been selected yet. Dependency and binary redistribution licenses will be documented before the first public release. In particular, an FFmpeg build can be LGPL or GPL depending on its configuration; Local Subtitle Studio currently invokes a user-installed executable and does not redistribute FFmpeg.
