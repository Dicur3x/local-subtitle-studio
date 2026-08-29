# Third-party component notices

Local Subtitle Studio does not commit or package the following binaries in its repository. The Components screen downloads them directly from their listed upstream source only after the user chooses **Install** or **Install / update**.

## FFmpeg and FFprobe

- Upstream project and source releases: <https://ffmpeg.org/download.html>
- Licensing and compliance guidance: <https://ffmpeg.org/legal.html>
- Windows essentials build source used by the component manager: <https://www.gyan.dev/ffmpeg/builds/>
- Managed build license: GPL version 3

The component manager retrieves the published version, ZIP, and SHA-256 file over HTTPS. It verifies the archive before activation and retains the complete extracted distribution, including its license and build-configuration files. The installed-component metadata records the archive hash and the corresponding upstream FFmpeg source-release URL.

The application invokes `ffmpeg.exe` and `ffprobe.exe` as separate processes. It does not link to FFmpeg libraries. Anyone redistributing an installer that embeds these binaries must perform a fresh GPL compliance review and provide the complete corresponding source and build information; this project currently embeds no such binaries.

## whisper.cpp

- Upstream source and releases: <https://github.com/ggml-org/whisper.cpp>
- License: MIT
- Copyright: 2023–2026 the ggml authors

The component manager selects a non-draft, non-prerelease semantic release and the official Windows x64 asset. A SHA-256 digest published in GitHub release metadata is verified when available; the downloaded archive hash is always recorded. A copy of the MIT license is placed in the managed installation.

## OpenAI Whisper model weights converted for whisper.cpp

- Model repository: <https://huggingface.co/ggerganov/whisper.cpp>
- Original project: <https://github.com/openai/whisper>
- License: MIT
- Copyright: 2022 OpenAI

Each supported model file has a fixed expected byte size and SHA-256 checksum in the application catalog. Both are verified before activation. A copy of the license is stored beside managed models.

## Silero VAD

- Model repository: <https://huggingface.co/ggml-org/whisper-vad>
- Upstream project: <https://github.com/snakers4/silero-vad>
- License: MIT
- Copyright: 2020–present Silero Team

The fixed `ggml-silero-v6.2.0.bin` artifact is verified by exact byte size and SHA-256 checksum before activation. A copy of the license is stored beside managed models.
