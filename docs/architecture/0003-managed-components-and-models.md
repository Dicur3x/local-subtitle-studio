# ADR 0003: Managed components, model catalog, and explicit updates

- Status: accepted for MVP 1
- Date: 2026-08-22

## Context

Requiring people to find several executables, models, and compatible versions defeats the intended one-click workflow. Committing or packaging third-party binaries in the application repository would also create avoidable release size, provenance, and redistribution obligations.

## Decision

Add an application-managed component directory below the user's Local AppData folder. The application downloads third-party artifacts directly from trusted upstream sources when the user explicitly requests installation:

- FFmpeg/FFprobe Windows essentials ZIP from the Windows provider linked by ffmpeg.org;
- stable semantic whisper.cpp Windows x64 releases from the official GitHub repository;
- the official llama.cpp Windows x64 CPU build associated with its stable release;
- fixed Whisper model artifacts from the official whisper.cpp model repository;
- a fixed Silero VAD model from the official whisper.cpp VAD repository.
- curated official Qwen3 GGUF translation profiles from the Qwen Hugging Face organization.

Do not perform silent background updates. **Check for updates** may query version metadata, while every download requires a separate user action. Successfully installed executable and model paths are applied to the existing settings automatically; Advanced settings preserves manual overrides.

## Integrity and safe activation

- All requests and final redirect targets must use HTTPS.
- Downloads and extracted archives have explicit size limits.
- ZIP paths are normalized and may not escape the staging directory.
- FFmpeg's published SHA-256 is required and verified.
- A GitHub-published whisper.cpp SHA-256 is verified when present; its computed hash is always recorded.
- The GitHub-published llama.cpp Windows archive SHA-256 is required and verified.
- Model and VAD byte sizes and SHA-256 values are fixed in the catalog and required.
- Translation-model repository, filename, byte size, SHA-256, and Apache-2.0 license file are fixed in the catalog and required.
- Downloads are staged below the managed component directory. Metadata is written atomically, and no incomplete artifact becomes current.
- The full FFmpeg build archive content is retained so its license and build information remain with the executables. MIT license texts are copied beside whisper.cpp and models.

## Version policy

FFmpeg follows the provider's current stable release. whisper.cpp ignores build-number tags, drafts, and prereleases and selects the newest stable semantic release containing the required Windows asset. llama.cpp resolves the official stable release to the Windows CPU build tag published by that release.

Whisper weights are immutable catalog artifacts, not tools with a conventional changelog. A model is identified by profile, filename, size, and checksum. A future catalog change can recommend a newer or different artifact without silently replacing the user's selection.

Translation weights follow the same immutable-catalog rule and remain separate from recognition. Installing or updating llama.cpp does not download or switch a Qwen model; selecting a translation profile is an explicit operation with its own displayed size and disk-space check.

## Speech timing consequence

Every managed recognition profile installs Silero VAD. VAD reduces silence sent to recognition and supplies speech boundaries, but it is not the complete subtitle-timing solution. The transcription stage must also use word timestamps and clamp cue ends near the detected end of speech, with a small configurable tail and a hard limit before the next speech segment.

## Consequences

- A normal user can prepare the local toolchain without finding executable paths.
- The Git repository and application package remain free of third-party binaries.
- Offline operation is possible after initial installation.
- Update checks require network access and can fail independently without changing the current working installation.
- A future packaged public release must repeat the license review for any third-party artifact it chooses to embed.
