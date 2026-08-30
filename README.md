# Local Subtitle Studio

[Русская версия](README.ru.md)

Local Subtitle Studio is a desktop application for creating subtitles from video while keeping media on the user's computer. The first supported platform is Windows.

The current implementation status and intentionally pending requirements are tracked in [`ROADMAP.md`](ROADMAP.md).

> Status: early MVP development. The current build can install its local toolchain and create an experimental original-language SRT locally from a selected video audio track.

## Current features

- Drag-and-drop or file picker for local video files.
- Asynchronous media inspection, so the UI remains responsive.
- Audio track details: language metadata, title, codec, channel layout, bitrate, and sample rate.
- One-click recognition setup plus a separate updater for FFmpeg/FFprobe, whisper.cpp, and llama.cpp that never changes the selected model.
- Download progress bars with transferred sizes and exact percentages when the source reports a total size.
- Release-note text inside the application after a version check, with an optional link to the upstream source.
- Fast, Balanced, and Maximum quality Whisper model profiles; each model installs together with Silero VAD.
- Separate Fast, Balanced, and Maximum quality local translation profiles based on official Qwen3 GGUF files. Translation models are optional and download only after an explicit click.
- HTTPS downloads, safe ZIP extraction, size limits, exact model checksums, and published component checksums when available.
- Automatic activation of managed tools, with manual path overrides in Advanced settings.
- Persistent paths for `ffmpeg`, `ffprobe`, `whisper-cli`, `llama-cli`, recognition/translation models, and temporary files. Manual technical paths stay collapsed for advanced users.
- Built-in tool-path validation from the Advanced settings dialog.
- Extraction of the selected stream to temporary 16 kHz, mono, signed 16-bit PCM WAV.
- Local whisper.cpp recognition with an integrated searchable choice of all 100 supported languages, Silero VAD, full token timestamps, and speech-boundary-aware subtitle timing.
- Long audio is recognized in overlapping eight-minute windows with a clean Whisper context, then merged back onto the original timeline; suspicious repetition is retried and a still-corrupt result is never saved.
- Unfinished work is recoverable after cancellation, closing, or a crash: prepared audio and every completed eight-minute recognition window are reused. Loading another video first shows a data-loss warning.
- A five-stage creation progress bar with the real percentage reported by whisper.cpp during recognition.
- Word-timestamp-aware splitting of long utterances, balanced line wrapping, and validation for overlaps, repeated text, line length, line count, and reading speed.
- Configurable readability and timing limits in Advanced settings, with safe defaults and automatic migration of older settings files.
- A visible readiness check for FFmpeg, whisper.cpp, the recognition model, and VAD before subtitle creation starts.
- Missing recognition components expose an **Open Components** action both beside the Create button and in the setup prompt.
- Conservative duplicate-cue cleanup, cautious restoration of unambiguous Russian `ё` forms, and low-confidence warnings; flagged cues can be reviewed and edited in the application without changing timestamps.
- UTF-8 SRT export beside the source video, in a `Subs` folder, or in a chosen folder; an existing subtitle file is never overwritten.
- English UI by default with an optional Russian translation, plus a minimal first-run guide.
- A self-contained Windows portable ZIP is published as an explicitly marked public alpha for early testing.
- Cancellation of active inspection, audio-preparation, and transcription processes.
- Automatic removal of temporary recognition audio after the operation.

No media is uploaded. Cloud services are not used by the current build.

## Run as an ordinary user

Download the latest Windows ZIP from [GitHub Releases](https://github.com/Dicur3x/local-subtitle-studio/releases):

1. Extract the app-image or portable ZIP.
2. Start `Local Subtitle Studio.exe`.
3. Choose the interface language and subtitle location in the first-run guide.
4. Let the guide open **Components**, then install the recommended tools and a model.
5. Choose a video, audio track, and spoken language, then select **Create SRT**.

The alpha is intended for early testing and includes its own Java runtime. The normal app image stores settings and managed downloads below `%LOCALAPPDATA%\LocalSubtitleStudio`. The portable ZIP contains a `portable.mode` marker and stores them in `data` beside the launcher. Neither mode changes the system `PATH` or requires administrator rights.

## Developer requirements

- A JDK 17 or newer to start Gradle. The build pins its daemon/compiler/runtime to JDK 21 and can provision that toolchain automatically when it is missing.
- Enough local disk space for the selected recognition model (about 190 MB, 574 MB, or 3.1 GB), an optional translation model (about 610 MB, 1.7 GB, or 2.3 GB), and temporary audio.
- Windows 10/11 for the currently tested build. The code avoids Windows-only process invocation so Linux and macOS can be added later.

FFmpeg/FFprobe, whisper.cpp, llama.cpp, and recognition/translation models can be installed from **Components**. Existing executables on `PATH` or custom files chosen in the collapsed advanced-path section remain supported.

## Run from IntelliJ IDEA or a terminal

```powershell
.\gradlew.bat run
```

Open **Components** and choose **Set up programs + recommended recognition model** to prepare FFmpeg/FFprobe, whisper.cpp, llama.cpp, the Balanced Whisper model, and Silero VAD in one operation. **Update FFmpeg, whisper.cpp, and llama.cpp** updates only program components and preserves every selected model. Recognition and translation models remain separately selectable. The application never installs an update silently. **Advanced settings** contains one storage folder for managed programs, models, and recoverable work; changing it affects future downloads and does not move existing files automatically.

After choosing a video and audio track, leave **Spoken language** on **Auto detect** or select it manually, then press **Create SRT**. The output location is selected in **Advanced settings**. If the destination name already exists, a numbered file is created instead.

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

The larger WAV is working data, not an audio-quality upgrade. During subtitle creation it is retained in the local recovery workspace so an interrupted feature film does not need to be decoded or recognized again from the beginning. It is removed after a successful SRT or when the user confirms starting incompatible work.

## Recovering interrupted work

After every completed eight-minute Whisper window, the application writes an atomic local checkpoint. On the next start it offers to restore the source file, audio track, spoken language, voice-over mode, prepared PCM, and completed recognition windows. The source path, size, and modification time are checked before reuse; changed or missing media is never resumed silently.

Only one unfinished single-file job is kept at this stage. Choosing another video displays the previous filename and offers to restore it, start the new file and remove the checkpoint, or cancel. A successfully written SRT clears the workspace automatically.

## Models, VAD, and subtitle timing

Whisper model files are immutable weights rather than applications with regular releases and changelogs. Local Subtitle Studio identifies each catalog entry by its exact filename, byte size, and SHA-256 checksum. When the recommended catalog profile changes, the Components screen can present the new profile; whisper.cpp itself has conventional release notes available from that screen.

Silero VAD is installed with every managed model. It identifies speech boundaries and avoids sending long silent spans to recognition. In whisper.cpp 1.9.2, JSON segment offsets are on the original audio timeline while raw full-JSON token offsets can remain on the silence-compressed VAD timeline. Local Subtitle Studio detects that mismatch, treats segment offsets as authoritative, and projects token positions back into each original segment. This fixes the accumulated “subtitles run early” error seen on real material with opening silence or music.

Long recognition segments are split at word-token boundaries before timing is optimized. The defaults target 42 characters per line, two lines, an 800 ms minimum duration, a seven-second maximum that prevents a short phrase from lingering through silence, and a 20 characters/second warning threshold. These values, together with speech padding and the next-speech gap, can be adjusted in **Advanced settings**.

Feature-length audio is additionally divided into overlapping eight-minute recognition windows. Every window starts a new whisper.cpp process, so a hallucinated phrase cannot carry its text context through the rest of a film. The overlap is assigned to exactly one window when results are merged. If a window still contains a long stuck phrase, the application retries it with text-context carry disabled; if the retry is also suspicious, no SRT is saved and the user sees the affected time.

## Recognition correction

Deterministic cleanup currently fixes spacing, line breaks, and close exact duplicates, then flags low-confidence cues. Repairing names, terminology, and contextually broken phrases needs a separate local language model. The planned optional stage uses `llama.cpp`, neighbouring cues, Whisper confidence, and a user glossary. It must return reviewable structured suggestions, retain the original transcript and edit log, and never change timestamps. See [ADR 0007](docs/architecture/0007-contextual-subtitle-correction.md).

## Mixed languages, translation, and voice-over

These cases are deliberately not presented as finished one-click features yet:

- For a mostly Russian track containing French dialogue, a fixed `ru` Whisper pass can turn French into Russian-looking phonetic hallucinations. A reliable mode needs speech-chunk language identification followed by language-specific recognition.
- Two future outputs are specified: an “author intent” subtitle that omits foreign dialogue the original audience was not meant to understand, and an “all dialogue” subtitle that includes a translated foreign line. A safe label such as `[French]` is preferable as a baseline; italics remain an optional style.
- whisper.cpp's built-in translation target is English, so French-to-Russian translation needs a separately managed local translation model or an explicit opt-in cloud provider. The application will not pretend that `--translate` can produce Russian.
- The UI now has an experimental mixed-voice-over switch. It requires an explicit target language so an English opening cannot make automatic detection select the wrong language for the whole file, and it marks the result for review. It does not yet separate the quieter original from the voice-over: both are already mixed into the same samples. Reliable dual-language extraction requires a separately evaluated source-separation pipeline and visible uncertainty.

See [ADR 0005](docs/architecture/0005-multilingual-dialogue-and-voice-over.md) for the proposed modes, filenames, and acceptance criteria.

Translation development is in progress. The Components screen can now install a checksummed official llama.cpp Windows build and one of three checksummed official Qwen3 GGUF profiles, while the implemented pipeline batches 12 target cues with neighbouring context, preserves cue IDs and timestamps, and rejects missing/duplicate/invented IDs. Translation is intentionally not exposed in the main creation flow until real English↔Russian quality and output tests pass. See [ADR 0008](docs/architecture/0008-local-subtitle-translation.md).

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
      → overlapping clean-context windows → original-timeline merge
      → token-aware segmentation → word-aware timing → validation/review
      → balanced formatting → non-overwriting SRT
```

## Licensing

Local Subtitle Studio source code is available under the [MIT License](LICENSE).

The repository does not contain FFmpeg, whisper.cpp, llama.cpp, or model binaries. The Components screen downloads them directly from the listed project sources at the user's request and retains license/build information. The current Windows FFmpeg essentials build is GPLv3; whisper.cpp, llama.cpp, OpenAI Whisper model weights, and Silero VAD are MIT-licensed; Qwen3 GGUF translation weights use Apache-2.0. See [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md) and [`docs/architecture/0003-managed-components-and-models.md`](docs/architecture/0003-managed-components-and-models.md).
