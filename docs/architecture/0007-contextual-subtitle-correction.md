# ADR 0007: Reviewable local contextual subtitle correction

Status: proposed

## Context

Speech recognition can produce plausible but wrong words, broken names, and phrases that only become clear from neighbouring dialogue. Spelling rules alone cannot repair those cases. At the same time, silently rewriting dialogue is unsafe: a language model can invent text, remove intentional speech errors, or change meaning.

Mixed voice-over is an audio-separation problem before it is a language-editing problem. A text model may improve punctuation or a recognized name, but it cannot recover words that Whisper never heard correctly because two voices overlap.

## Decision

Add correction as an optional, separate local stage after timing optimization and before final export. It must not be part of the default recognition path until it passes a labelled subtitle test set.

The planned runtime is `llama.cpp` with a vetted GGUF model. `llama.cpp` supports local GGUF inference and schema-constrained output; its server also exposes a local API. A multilingual instruction model in the Qwen family is an initial candidate because the official Qwen3 release includes small dense models, supports 119 languages and dialects, and uses Apache 2.0. The exact model and quantization remain undecided until Russian, English, French, code-switching, RAM use, CPU speed, provenance, hashes, and licence notices are measured.

Official references:

- <https://github.com/ggml-org/llama.cpp>
- <https://github.com/ggml-org/llama.cpp/tree/master/grammars>
- <https://qwenlm.github.io/blog/qwen3/>
- <https://huggingface.co/Qwen/Qwen3-4B>

## Correction contract

For each batch, the model receives:

- immutable cue identifiers and timestamps;
- the current cue plus a limited window of preceding and following cues;
- Whisper token confidence and detected language;
- an optional user glossary for names, places, and series terminology;
- an instruction to preserve meaning, register, intentional mistakes, and foreign speech markers.

The response is constrained to structured JSON containing cue id, original text, proposed text, short reason, and confidence. The application rejects a response if cue ids, order, or count change; if timestamps appear; if text is added for a silent interval; or if the output does not match the schema.

The model may suggest punctuation, casing, word-boundary, agreement, likely recognition, and glossary corrections. It may not translate unless the user selected a translation workflow, combine different speakers, change timings, or replace uncertain foreign speech with invented target-language dialogue.

## User experience

Correction is shown as its own progress stage and produces a review screen with original and proposed text side by side. Each change can be accepted or rejected. A cautious automatic mode may later apply only high-confidence mechanical edits, but it always retains the original transcription and an edit log.

Model installation remains separate from Whisper model selection. The Components screen will show runtime and language-model size, RAM guidance, licence, source, installed version, and update availability. Nothing downloads or updates silently.

## Acceptance criteria

- A labelled corpus measures word-error changes and, separately, semantic regressions.
- Russian, English, French, mixed-language dialogue, names, slang, intentional grammar errors, and MVO failures are represented.
- The corrected output never changes cue count, ids, order, or timestamps without an explicit timing operation.
- Every proposed text change is reproducible in an edit log and can be reverted.
- Processing remains offline and binds any local server only to loopback.
- Low-memory machines can skip the stage without losing SRT creation.

Until these criteria pass, Local Subtitle Studio only performs deterministic cleanup and surfaces low-confidence cues for review.
