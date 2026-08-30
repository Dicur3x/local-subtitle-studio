# Local Subtitle Studio roadmap

This file keeps product requirements visible outside the chat history. “Planned” means the behavior is not presented as ready in the application.

## Working baseline

- [x] Drag-and-drop or choose one video.
- [x] Inspect container audio tracks with ffprobe and choose a stream.
- [x] Install/update FFmpeg, ffprobe, whisper.cpp, Whisper models, and VAD from upstream sources.
- [x] Create an original-language SRT locally with word-aware timing, VAD, long-form chunking, loop detection, and non-overwriting output.
- [x] English and Russian UI, first-run setup, output-location choices, component release notes, progress percentage, and cancellation.
- [x] Searchable spoken-language selector and correct switching between already downloaded Whisper models.
- [x] Focused review editor for cues flagged by validation.
- [x] Conservative Russian `ё` restoration for unambiguous forms.
- [x] Windows app-image/portable build tasks and a project-specific application icon.
- [x] Full-film long-form test on the 93-minute *Resolution* sample without the previous repetition failure.

## Translation — in progress

- [x] Replaceable `TranslationEngine` boundary.
- [x] Context batches with stable cue IDs and strict missing/duplicate/invented-ID rejection.
- [x] Local llama.cpp structured-output adapter.
- [x] Managed llama.cpp installation and curated, licensed Qwen3 GGUF profiles with exact checksums and disk-size display.
- [ ] Main-screen source/target language controls without duplicating the existing language search UI.
- [ ] Explicit output choices: original, translated, both separate files, and optional bilingual SRT.
- [ ] English→Russian and Russian→English real-media quality/runtime tests.
- [ ] Translation review, cancellation, recovery, and localized errors in the complete UI pipeline.

See [ADR 0008](docs/architecture/0008-local-subtitle-translation.md).

## Next milestones

### Context correction

- [ ] Optional local post-ASR correction with neighbouring cues and a user glossary.
- [ ] Before/after diff, explicit review, unchanged timestamps, and no silent rewriting of names, numbers, negation, or foreign speech.

### Mixed languages and voice-over

- [ ] Detect stable language regions instead of treating one automatic language guess as proof.
- [ ] Author-intent and all-dialogue subtitle variants for foreign-language passages.
- [ ] Evaluate MVO against isolated stems; never describe speaker diarization as source separation.
- [ ] Language labels and optional styling tested in common SRT players.

### Multiple files and tracks

- [ ] Batch queue with per-file status, cancellation, retry, collision-safe output, and a final summary.
- [ ] Select more than one audio track and choose recognition/translation outputs per track.
- [ ] Reuse a loaded local model across queue items where safe.

Batch processing starts only after the single-file translation path is verified end to end.

### Performance and resilience

- [ ] Detect CPU/GPU capabilities and offer compatible acceleration without requiring NVIDIA.
- [x] Resumable prepared audio and per-window recognition checkpoints so a late interruption does not restart a feature film from zero.
- [ ] Managed model removal, storage usage, integrity recheck, and cleanup of abandoned downloads.
- [ ] Persistent diagnostic log files with a privacy-conscious export action.

### Distribution and platforms

- [ ] Repeatable Windows release workflow, signed artifacts when available, checksums, and release notes.
- [ ] Publish a user build only after installation/portable/update testing on clean Windows systems.
- [ ] Linux and macOS packaging after the Windows workflow is stable.
- [ ] Additional exports such as WebVTT, ASS, JSON, and plain transcript text.
