# ADR 0002: External tools and transcription-ready audio

- Status: accepted for MVP 1
- Date: 2026-08-22

## Decision

Keep FFmpeg, FFprobe, whisper.cpp, and model locations in persistent user settings. FFmpeg and FFprobe are required for the current media workflow; whisper.cpp and a model are optional until transcription is implemented.

Do not bundle an FFmpeg binary in the MVP. A later packaging decision may include one after selecting a specific build and documenting compliance with its LGPL/GPL configuration.

Decode only the user-selected audio stream into a temporary WAV with these properties:

- signed 16-bit little-endian PCM;
- 16,000 Hz sample rate;
- one channel;
- no video, subtitle, or data streams.

The temporary output belongs to a unique working directory. The application removes it when the prepared track becomes stale or the application closes normally.

## Rationale

Speech recognition needs decoded samples, not AC-3, AAC, Opus, or another container stream. PCM conversion is decoding and normalization, not quality enhancement: information missing from the source is not recreated. A consistent waveform format keeps the recognizer boundary deterministic and matches the current whisper.cpp command-line workflow.

User-selected executable paths avoid assuming a particular Windows installation layout. Saving those paths outside the repository also keeps machine-specific configuration out of source control.

## Command boundary

Audio preparation is represented by the application-owned `AudioExtractor` interface. The FFmpeg adapter invokes an argument list equivalent to:

```text
ffmpeg -nostdin -hide_banner -loglevel error -y -i <video>
       -map 0:<stream-index> -vn -sn -dn -ac 1 -ar 16000
       -c:a pcm_s16le <temporary.wav>
```

No shell is involved. The external-process runner drains output concurrently and terminates FFmpeg when the operation is cancelled.

## Consequences

- WAV working files are larger than compressed source audio but are temporary and predictable.
- Normal application shutdown provides deterministic cleanup; abnormal process termination may leave a temporary directory for later housekeeping.
- The settings format is versioned JSON so it can evolve without coupling the UI to storage details.
- Bundling and automatic installation of third-party tools remain separate, future distribution decisions.
