# Local Subtitle Studio

[Русская версия](README.ru.md)

> **Development has stopped.** Local Subtitle Studio remains available as an MIT-licensed research prototype, but it is no longer being developed or recommended as a production subtitle application. No further releases, dependency updates, or user support are planned.

Local Subtitle Studio is an experimental Windows desktop application that creates SRT subtitles locally, without uploading the source video. The public [Alpha 2 release](https://github.com/Dicur3x/local-subtitle-studio/releases/tag/v0.1.0-alpha.2) demonstrates a working single-file recognition pipeline, but it is unfinished software and may produce inaccurate text or timing.

## Why development stopped

The project began as an attempt to make local subtitle creation approachable for a non-technical user. A later comparison showed that mature open-source applications already cover most of the intended product substantially better:

- [Subtitle Edit](https://github.com/SubtitleEdit/subtitleedit) combines a full subtitle editor with waveform and synchronization tools, speech recognition through multiple engines (including whisper.cpp and Faster-Whisper-XXL), local or online translation, batch processing, and review tools.
- [Buzz](https://github.com/chidiwilliams/buzz) offers a simpler cross-platform desktop workflow for local transcription and translation with several recognition engines.
- [Faster-Whisper-XXL](https://github.com/Purfview/whisper-standalone-win) is a feature-rich Windows recognition engine and command-line tool. It can also be used from Subtitle Edit.

Continuing Local Subtitle Studio as another general-purpose subtitle application would mostly duplicate those projects while providing a less mature editor, recognition ecosystem, translation workflow, and hardware acceleration. The responsible decision is therefore to stop rather than ask users or contributors to invest in a redundant alpha.

For actual subtitle work, start with **Subtitle Edit**. If its interface feels too dense, try **Buzz**. On an AMD GPU, whisper.cpp with Vulkan is a sensible first recognition backend; Faster-Whisper acceleration is primarily oriented around NVIDIA CUDA.

## What the prototype implemented

Alpha 2 includes:

- local inspection of video and audio tracks with FFprobe;
- managed installation of FFmpeg, whisper.cpp, Whisper models, and Silero VAD;
- selectable audio track and spoken language;
- recognition in overlapping eight-minute windows with clean Whisper context;
- recovery after cancellation, closing, or a crash by reusing prepared audio and completed windows;
- protection against long repeated or hallucinated recognition loops;
- speech-boundary-aware timing, readable line wrapping, and SRT validation;
- a small review editor for flagged cues;
- collision-safe UTF-8 SRT export beside the video, in a `Subs` directory, or in a chosen directory;
- English and Russian user interfaces;
- a self-contained Windows portable build.

The recovery/checkpoint design is the most distinctive part of the prototype and may be useful as a reference for contributions to an established project. Architecture notes are kept in [`docs/architecture`](docs/architecture), and the historical scope is recorded in [`ROADMAP.md`](ROADMAP.md).

The unfinished translation work on a development branch is not part of Alpha 2 and will not be released as a supported Alpha 3.

## Using the final alpha

The final public build is [v0.1.0-alpha.2](https://github.com/Dicur3x/local-subtitle-studio/releases/tag/v0.1.0-alpha.2):

1. Download and extract the portable Windows ZIP.
2. Start `Local Subtitle Studio.exe`.
3. Open **Components** and install the required programs and a recognition model.
4. Select a video, audio track, and spoken language, then choose **Create SRT**.

The build includes its own Java runtime. In portable mode, settings, downloaded components, models, and recovery data are stored in the `data` directory beside the application. Media stays on the local computer.

This release is preserved for evaluation only. Managed download URLs, checksums, and third-party versions can become outdated, and there will be no maintenance updates. Review every generated subtitle before relying on it.

## Building from source

The project is a JavaFX application built with Gradle and JDK 21; it is not a Spring Boot application.

```powershell
.\gradlew.bat run
```

Run unit tests:

```powershell
.\gradlew.bat test
```

Build a self-contained Windows app image or portable ZIP:

```powershell
.\gradlew.bat packageAppImage
.\gradlew.bat packagePortableZip
```

The source remains available for study and reuse, but issues and pull requests should not be opened with an expectation of response or release.

## Licensing

Local Subtitle Studio source code is available under the [MIT License](LICENSE).

The repository does not contain FFmpeg, whisper.cpp, or model binaries. Alpha 2 downloads selected third-party components at the user's request. Their licenses and origins are documented in [`THIRD-PARTY-NOTICES.md`](THIRD-PARTY-NOTICES.md). Those notices do not imply continuing compatibility or security maintenance.
