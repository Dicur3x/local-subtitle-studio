# ADR 0009: Replaceable local recognition engines

- Status: proposed
- Date: 2026-08-30

## Context

The first working release uses whisper.cpp because it has source code, an MIT licence, portable CPU builds, GGML models, and a small integration surface. That made it suitable for validating audio-track selection, recoverable long-form processing, timing cleanup, review, and non-overwriting output.

It is not the only or automatically the best runtime. [Faster-Whisper-XXL](https://github.com/Purfview/whisper-standalone-win) packages CTranslate2-based Faster Whisper with movie-oriented defaults, automatic CUDA selection, batched inference, multiple VAD implementations, vocal extraction, diarization, and recursive batch input. [Subtitle Edit](https://subtitleedit.github.io/subtitleedit/features/speech-to-text.html) already integrates Faster-Whisper-XXL and several other local ASR engines in a mature subtitle editor.

As checked on 2026-08-30, the Faster-Whisper-XXL wrapper repository contains a README and changelog but no wrapper source or declared repository licence. Its upstream Faster Whisper project is MIT-licensed, but that alone does not establish redistribution terms for the modified standalone wrapper and its complete binary bundle.

## Decision

Recognition becomes a replaceable product capability rather than a permanent whisper.cpp dependency.

1. Keep whisper.cpp as the current open, reproducible reference backend.
2. Define a recognition-engine boundary that returns the same stable cues, language, token confidence, timing data, progress, and recoverable checkpoints expected by the rest of the application.
3. Add user-supplied Faster-Whisper-XXL as the first optional backend only after its output and cancellation behaviour are covered by tests.
4. Do not bundle or automatically download Faster-Whisper-XXL unless the wrapper author publishes clear terms that permit that distribution workflow.
5. Benchmark engines on the same full-film samples. Record recognition quality, stuck/repeated cues, timing offset, CPU/GPU runtime, RAM/VRAM, cancellation, and restart cost rather than choosing by brand name.
6. Evaluate whisper.cpp Vulkan builds for AMD and Intel GPUs, while Faster-Whisper-XXL/CTranslate2 remains especially relevant to NVIDIA CUDA systems.
7. Never replace a user's selected engine during an application or component update.

## Product boundary

Local Subtitle Studio is not intended to win by hiding more command-line flags. Its distinct scope is a novice-facing, recoverable workflow across audio-track selection, recognition, focused review, separate per-language SRT files, optional local contextual translation, component provenance, and safe storage. Where a mature engine already solves recognition better, the application should integrate or interoperate with it instead of recreating it.

## Acceptance criteria

- The existing *Resolution* long-form fixture produces no new timing regression or repeated tail cues on either engine.
- A backend interruption can resume without presenting an old checkpoint from another engine as compatible.
- Engine-specific models and settings are clearly named and do not leak into the common UI unless they materially affect ordinary users.
- The application explains CPU, NVIDIA CUDA, and AMD/Intel Vulkan availability before a large model download.
- User-supplied executable validation reports its detected version and fails without altering existing subtitle files.

## Consequences

Faster-Whisper-XXL becomes a serious planned backend and benchmark, not an ignored competitor. The current translation work can continue independently because it consumes reviewed subtitle cues rather than a specific recognition implementation. Batch processing remains after a single-file backend comparison and recovery test.
