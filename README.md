# Local Subtitle Studio

[Русская версия](README.ru.md)

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
- A visible readiness check for FFmpeg, whisper.cpp, the recognition model, and VAD before subtitle creation starts.
- Conservative duplicate-cue cleanup plus low-confidence warnings for passages that need human review; recognized dialogue is never silently rewritten.
- UTF-8 SRT export beside the source video, in a `Subs` folder, or in a chosen folder; an existing subtitle file is never overwritten.
- English UI by default with an optional Russian translation, plus a minimal first-run guide.
- Self-contained Windows app image and portable ZIP packaging; the user does not need to install Java or Gradle.
- Cancellation of active inspection, audio-preparation, and transcription processes.
- Automatic removal of temporary recognition audio after the operation.

No media is uploaded. Cloud services are not used by the current build.

## Run as an ordinary user

Use a packaged Windows build when one is attached to a private test release:

1. Extract the app-image or portable ZIP.
2. Start `Local Subtitle Studio.exe`.
3. Choose the interface language and subtitle location in the first-run guide.
4. Let the guide open **Components**, then install the recommended tools and a model.
5. Choose a video, audio track, and spoken language, then select **Create original SRT**.

The packaged application includes its own Java runtime. The normal app image stores settings and managed downloads below `%LOCALAPPDATA%\LocalSubtitleStudio`. The portable ZIP contains a `portable.mode` marker and stores them in `data` beside the launcher. Neither mode changes the system `PATH` or requires administrator rights.

## Developer requirements

- A JDK 17 or newer to start Gradle. The build pins its daemon/compiler/runtime to JDK 21 and can provision that toolchain automatically when it is missing.
- Enough local disk space for the selected model (about 190 MB, 574 MB, or 3.1 GB) and temporary audio.
- Windows 10/11 for the currently tested build. The code avoids Windows-only process invocation so Linux and macOS can be added later.

FFmpeg/FFprobe, whisper.cpp, and a model can be installed from **Components**. Existing executables on `PATH` or custom files chosen in **Advanced settings** remain supported.

## Run from IntelliJ IDEA or a terminal

```powershell
.\gradlew.bat run
```

Open **Components** and choose **Set up recommended tools + model** to prepare FFmpeg/FFprobe, stable whisper.cpp, the Balanced Whisper model, and Silero VAD in one operation. **Update FFmpeg + whisper.cpp** updates only those program components and preserves the selected model. Models remain separately selectable. The application never installs an update silently. Open **Advanced settings** only when you want to override a managed path or change the temporary directory.

After choosing a video and audio track, leave **Spoken language** on **Auto detect** or select it manually, then press **Create original SRT**. The output location is selected in **Advanced settings**. If the destination name already exists, a numbered file is created instead.

Build a self-contained Windows app image:

```powershell
.\gradlew.bat packageAppImage
```

Build a portable ZIP whose settings, components, and models stay beside the app:

```powershell
.\gradlew.bat packagePortableZip
```

The status area shows preparation, recognition, timing, validation, and saving as separate stages. During recognition, the overall bar is driven by whisper.cpp's own progress callback. A completed file stays at 100%; validation warnings remain visible without discarding an otherwise usable SRT.

On Windows, settings and managed downloads are stored below `%LOCALAPPDATA%\LocalSubtitleStudio`. They are kept outside the project and are not committed to Git. A cancelled or failed download is never activated.

Environment variables such as `LSS_FFMPEG_PATH` and `LSS_FFPROBE_PATH` can still provide first-run defaults.

## Why temporary WAV exists

Creating WAV does not improve or upscale compressed AC-3, AAC, or other source audio. FFmpeg decodes the selected stream into the exact uncompressed PCM format expected by the recognition pipeline. The source video is opened read-only and remains unchanged.

The larger WAV is short-lived working data. It is deleted when another track or video is chosen, when it is prepared again, or when the application exits normally.

## Models, VAD, and subtitle timing

Whisper model files are immutable weights rather than applications with regular releases and changelogs. Local Subtitle Studio identifies each catalog entry by its exact filename, byte size, and SHA-256 checksum. When the recommended catalog profile changes, the Components screen can present the new profile; whisper.cpp itself has conventional release notes available from that screen.

Silero VAD is installed with every managed model. It identifies speech boundaries and avoids sending long silent spans to recognition. In whisper.cpp 1.9.2, JSON segment offsets are on the original audio timeline while raw full-JSON token offsets can remain on the silence-compressed VAD timeline. Local Subtitle Studio detects that mismatch, treats segment offsets as authoritative, and projects token positions back into each original segment. This fixes the accumulated “subtitles run early” error seen on real material with opening silence or music.

Long recognition segments are split at word-token boundaries before timing is optimized. The defaults target 42 characters per line, two lines, an 800 ms minimum duration, and a 20 characters/second warning threshold. These values, together with speech padding and the next-speech gap, can be adjusted in **Advanced settings**.

## Mixed languages, translation, and voice-over

These cases are deliberately not presented as finished one-click features yet:

- For a mostly Russian track containing French dialogue, a fixed `ru` Whisper pass can turn French into Russian-looking phonetic hallucinations. A reliable mode needs speech-chunk language identification followed by language-specific recognition.
- Two future outputs are specified: an “author intent” subtitle that omits foreign dialogue the original audience was not meant to understand, and an “all dialogue” subtitle that includes a translated foreign line. A safe label such as `[French]` is preferable as a baseline; italics remain an optional style.
- whisper.cpp's built-in translation target is English, so French-to-Russian translation needs a separately managed local translation model or an explicit opt-in cloud provider. The application will not pretend that `--translate` can produce Russian.
- MVO usually contains the Russian voice-over and quieter original speech mixed into the same samples. Stereo diarization or speaker-turn detection does not separate those sources. The default remains transcription of the selected target-language track. Reliable dual-language extraction will require a separately evaluated source-separation pipeline and should expose uncertainty rather than silently combining both voices.

See [ADR 0005](docs/architecture/0005-multilingual-dialogue-and-voice-over.md) for the proposed modes, filenames, and acceptance criteria.

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
