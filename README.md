# Local Subtitle Studio

Local Subtitle Studio is a desktop application for creating subtitles from video while keeping media on the user's computer. The first supported platform is Windows.

> Status: early MVP development. The current build can install its local toolchain, inspect a video, let the user choose an audio track, and prepare that track for local transcription.

## Current features

- Drag-and-drop or file picker for local video files.
- Asynchronous media inspection, so the UI remains responsive.
- Audio track details: language metadata, title, codec, channel layout, bitrate, and sample rate.
- One-click recommended setup plus individual installation and manual update checks for FFmpeg/FFprobe and stable whisper.cpp releases.
- Fast, Balanced, and Maximum quality Whisper model profiles; each model installs together with Silero VAD.
- HTTPS downloads, safe ZIP extraction, size limits, exact model checksums, and published component checksums when available.
- Automatic activation of managed tools, with manual path overrides in Advanced settings.
- Persistent paths for `ffmpeg`, `ffprobe`, `whisper-cli`, Whisper and VAD models, and temporary files.
- Built-in tool-path validation from the Advanced settings dialog.
- Extraction of the selected stream to temporary 16 kHz, mono, signed 16-bit PCM WAV.
- Cancellation of active inspection and audio-preparation processes.
- Automatic removal of prepared audio when it is replaced or the application closes.

No media is uploaded. Cloud services are not used by the current build.

## Requirements

- A JDK 17 or newer to start Gradle. The build pins its daemon/compiler/runtime to JDK 21 and can provision that toolchain automatically when it is missing.
- Enough local disk space for the selected model (about 190 MB, 574 MB, or 3.1 GB) and temporary audio.
- Windows 10/11 for the currently tested build. The code avoids Windows-only process invocation so Linux and macOS can be added later.

FFmpeg/FFprobe, whisper.cpp, and a model can be installed from **Components**. Existing executables on `PATH` or custom files chosen in **Advanced settings** remain supported. `whisper-cli` and the models are prepared for the transcription milestone but are not invoked by the current build yet.

## Run

```powershell
.\gradlew.bat run
```

Open **Components** and choose **Set up recommended** to prepare FFmpeg/FFprobe, stable whisper.cpp, the Balanced Whisper model, and Silero VAD in one operation. The same screen also supports individual installation and update checks. The application never installs an update silently. Open **Advanced settings** only when you want to override a managed path or change the temporary directory.

On Windows, settings and managed downloads are stored below `%LOCALAPPDATA%\LocalSubtitleStudio`. They are kept outside the project and are not committed to Git. A cancelled or failed download is never activated.

Environment variables such as `LSS_FFMPEG_PATH` and `LSS_FFPROBE_PATH` can still provide first-run defaults.

## Why temporary WAV exists

Creating WAV does not improve or upscale compressed AC-3, AAC, or other source audio. FFmpeg decodes the selected stream into the exact uncompressed PCM format expected by the recognition pipeline. The source video is opened read-only and remains unchanged.

The larger WAV is short-lived working data. It is deleted when another track or video is chosen, when it is prepared again, or when the application exits normally.

## Models, VAD, and subtitle timing

Whisper model files are immutable weights rather than applications with regular releases and changelogs. Local Subtitle Studio identifies each catalog entry by its exact filename, byte size, and SHA-256 checksum. When the recommended catalog profile changes, the Components screen can present the new profile; whisper.cpp itself has conventional release notes available from that screen.

Silero VAD is installed with every managed model. It identifies speech boundaries and avoids sending long silent spans to recognition. This is one part of preventing a subtitle from staying visible until the next sentence. The transcription milestone will also clamp cue endings against detected speech and word timestamps; changing to a larger recognition model alone cannot reliably correct that timing problem.

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

The repository does not contain FFmpeg, whisper.cpp, or model binaries. The Components screen downloads them directly from the listed project sources at the user's request and retains license/build information delivered in the archives. The current Windows FFmpeg essentials build is GPLv3; whisper.cpp, OpenAI Whisper model weights, and Silero VAD are MIT-licensed. See [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) and [`docs/architecture/0003-managed-components-and-models.md`](docs/architecture/0003-managed-components-and-models.md).

A source-code license for Local Subtitle Studio itself has not been selected yet and must be chosen before a public release.
