# Local Subtitle Studio

Local Subtitle Studio is a desktop application for creating subtitles from video while keeping media on the user's computer. The first supported platform is Windows.

> Status: early MVP development. The current build can install its local toolchain and create an experimental original-language SRT locally from a selected video audio track.

## Current features

- Drag-and-drop or file picker for local video files.
- Asynchronous media inspection, so the UI remains responsive.
- Audio track details: language metadata, title, codec, channel layout, bitrate, and sample rate.
- One-click recommended setup plus a separate updater for FFmpeg/FFprobe and stable whisper.cpp releases that never changes the selected model.
- Download progress bars with transferred sizes and exact percentages when the source reports a total size.
- Release-note text inside the application after a version check, with an optional link to the upstream source.
- Fast, Balanced, and Maximum quality Whisper model profiles; each model installs together with Silero VAD.
- HTTPS downloads, safe ZIP extraction, size limits, exact model checksums, and published component checksums when available.
- Automatic activation of managed tools, with manual path overrides in Advanced settings.
- Persistent paths for `ffmpeg`, `ffprobe`, `whisper-cli`, Whisper and VAD models, and temporary files.
- Built-in tool-path validation from the Advanced settings dialog.
- Extraction of the selected stream to temporary 16 kHz, mono, signed 16-bit PCM WAV.
- Local whisper.cpp recognition with automatic language detection or a manual choice from all 100 supported languages, Silero VAD, full token timestamps, and speech-boundary-aware subtitle timing.
- A five-stage creation progress bar with the real percentage reported by whisper.cpp during recognition.
- Word-timestamp-aware splitting of long utterances, balanced line wrapping, and validation for overlaps, repeated text, line length, line count, and reading speed.
- Configurable readability and timing limits in Advanced settings, with safe defaults and automatic migration of older settings files.
- UTF-8 SRT export beside the source video; an existing subtitle file is never overwritten.
- Cancellation of active inspection, audio-preparation, and transcription processes.
- Automatic removal of temporary recognition audio after the operation.

No media is uploaded. Cloud services are not used by the current build.

## Requirements

- A JDK 17 or newer to start Gradle. The build pins its daemon/compiler/runtime to JDK 21 and can provision that toolchain automatically when it is missing.
- Enough local disk space for the selected model (about 190 MB, 574 MB, or 3.1 GB) and temporary audio.
- Windows 10/11 for the currently tested build. The code avoids Windows-only process invocation so Linux and macOS can be added later.

FFmpeg/FFprobe, whisper.cpp, and a model can be installed from **Components**. Existing executables on `PATH` or custom files chosen in **Advanced settings** remain supported.

## Run

```powershell
.\gradlew.bat run
```

Open **Components** and choose **Set up recommended tools + model** to prepare FFmpeg/FFprobe, stable whisper.cpp, the Balanced Whisper model, and Silero VAD in one operation. **Update FFmpeg + whisper.cpp** updates only those program components and preserves the selected model. Models remain separately selectable. The application never installs an update silently. Open **Advanced settings** only when you want to override a managed path or change the temporary directory.

After choosing a video and audio track, leave **Spoken language** on **Auto detect** or select it manually, then press **Create original SRT**. The result is saved beside the video as `<video>.<language>.srt`. If that name already exists, a numbered file is created instead.

The status area shows preparation, recognition, timing, validation, and saving as separate stages. During recognition, the overall bar is driven by whisper.cpp's own progress callback. A completed file stays at 100%; validation warnings remain visible without discarding an otherwise usable SRT.

On Windows, settings and managed downloads are stored below `%LOCALAPPDATA%\LocalSubtitleStudio`. They are kept outside the project and are not committed to Git. A cancelled or failed download is never activated.

Environment variables such as `LSS_FFMPEG_PATH` and `LSS_FFPROBE_PATH` can still provide first-run defaults.

## Why temporary WAV exists

Creating WAV does not improve or upscale compressed AC-3, AAC, or other source audio. FFmpeg decodes the selected stream into the exact uncompressed PCM format expected by the recognition pipeline. The source video is opened read-only and remains unchanged.

The larger WAV is short-lived working data. It is deleted when another track or video is chosen, when it is prepared again, or when the application exits normally.

## Models, VAD, and subtitle timing

Whisper model files are immutable weights rather than applications with regular releases and changelogs. Local Subtitle Studio identifies each catalog entry by its exact filename, byte size, and SHA-256 checksum. When the recommended catalog profile changes, the Components screen can present the new profile; whisper.cpp itself has conventional release notes available from that screen.

Silero VAD is installed with every managed model. It identifies speech boundaries and avoids sending long silent spans to recognition. Recognition requests full token timestamps from whisper.cpp, and the timing optimizer adds only a short tail after the last recognized word while keeping a gap before the next speech segment. This prevents a cue from being extended merely to meet the next sentence; changing to a larger recognition model alone cannot reliably correct timing.

Long recognition segments are split at word-token boundaries before timing is optimized. The defaults target 42 characters per line, two lines, an 800 ms minimum duration, and a 20 characters/second warning threshold. These values, together with speech padding and the next-speech gap, can be adjusted in **Advanced settings**.

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

The MVP intentionally starts as one Gradle module. Packages and interfaces separate UI, settings, media inspection, audio preparation, and external-process infrastructure. See the decisions in [`docs/architecture`](docs/architecture), including the [managed-component policy](docs/architecture/0003-managed-components-and-models.md) and [original SRT pipeline](docs/architecture/0004-original-srt-pipeline.md).

Current original-subtitle pipeline:

```text
video → audio track → temporary PCM → Silero VAD → whisper.cpp full JSON
      → token-aware segmentation → word-aware timing → validation
      → balanced formatting → non-overwriting SRT
```

## Licensing

The repository does not contain FFmpeg, whisper.cpp, or model binaries. The Components screen downloads them directly from the listed project sources at the user's request and retains license/build information delivered in the archives. The current Windows FFmpeg essentials build is GPLv3; whisper.cpp, OpenAI Whisper model weights, and Silero VAD are MIT-licensed. See [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) and [`docs/architecture/0003-managed-components-and-models.md`](docs/architecture/0003-managed-components-and-models.md).

A source-code license for Local Subtitle Studio itself has not been selected yet and must be chosen before a public release.
