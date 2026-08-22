# Local Subtitle Studio

Local Subtitle Studio is a desktop application for creating subtitles from video while keeping media on the user's computer. The first supported platform is Windows.

> Status: early MVP development. The current build can inspect a video, let the user choose an audio track, and prepare that track for local transcription.

## Current features

- Drag-and-drop or file picker for local video files.
- Asynchronous media inspection, so the UI remains responsive.
- Audio track details: language metadata, title, codec, channel layout, bitrate, and sample rate.
- Persistent paths for `ffmpeg`, `ffprobe`, the future `whisper-cli`, its model, and temporary files.
- Built-in tool-path validation from the Settings dialog.
- Extraction of the selected stream to temporary 16 kHz, mono, signed 16-bit PCM WAV.
- Cancellation of active inspection and audio-preparation processes.
- Automatic removal of prepared audio when it is replaced or the application closes.

No media is uploaded. Cloud services are not used by the current build.

## Requirements

- A JDK 17 or newer to start Gradle. The build pins its daemon/compiler/runtime to JDK 21 and can provision that toolchain automatically when it is missing.
- `ffmpeg` and `ffprobe` available on `PATH`, or selected in the application's Settings dialog.
- Windows 10/11 for the currently tested build. The code avoids Windows-only process invocation so Linux and macOS can be added later.

`whisper-cli` and a Whisper model are visible in Settings but remain optional until the transcription milestone is implemented.

## Run

```powershell
.\gradlew.bat run
```

Open **Settings** in the application to choose executables and a temporary directory. On Windows the choices are stored in `%LOCALAPPDATA%\LocalSubtitleStudio\settings.json`. The file is kept outside the project and is not committed to Git.

Environment variables such as `LSS_FFMPEG_PATH` and `LSS_FFPROBE_PATH` can still provide first-run defaults.

## Why temporary WAV exists

Creating WAV does not improve or upscale compressed AC-3, AAC, or other source audio. FFmpeg decodes the selected stream into the exact uncompressed PCM format expected by the recognition pipeline. The source video is opened read-only and remains unchanged.

The larger WAV is short-lived working data. It is deleted when another track or video is chosen, when it is prepared again, or when the application exits normally.

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

The MVP intentionally starts as one Gradle module. Packages and interfaces separate UI, settings, media inspection, audio preparation, and external-process infrastructure. See [`docs/architecture/0001-foundation.md`](docs/architecture/0001-foundation.md) and [`docs/architecture/0002-external-tools-and-audio.md`](docs/architecture/0002-external-tools-and-audio.md).

Planned pipeline:

```text
video → audio track → PCM audio → speech detection → transcription
      → timing/segmentation → validation → SRT
```

## Licensing

A source-code license has not been selected yet. Dependency and binary redistribution licenses will be documented before the first public release. FFmpeg can be redistributed when the applicable LGPL/GPL obligations are satisfied, but the exact obligations depend on how that FFmpeg binary was built. The current MVP therefore invokes user-selected executables and does not bundle FFmpeg.
