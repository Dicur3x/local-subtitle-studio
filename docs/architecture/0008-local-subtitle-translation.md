# ADR 0008: Context-preserving local subtitle translation

- Status: accepted; managed engine/model foundation complete, end-to-end quality work in progress
- Date: 2026-08-29

## Context

The original transcript must remain the source of truth. whisper.cpp can translate recognised speech to English, but it cannot translate an English transcript to Russian or provide arbitrary target languages. Subtitle translation also cannot be performed as unrelated single lines: names, pronouns, register, and sentence fragments need neighbouring dialogue.

Translation output must never change cue IDs or timestamps. Model output is not trusted merely because it is valid JSON: missing, duplicated, or invented cue IDs would corrupt the relationship between text and the original timeline.

## Decision

### Replaceable engine boundary

`TranslationEngine` accepts a language pair and a contextual `TranslationBatch`, then returns only `ID → translated text`. The orchestration layer is independent of a concrete local or optional cloud backend.

The first implementation is local structured generation through [llama.cpp](https://github.com/ggml-org/llama.cpp). Components can install an official checksummed Windows x64 CPU build and three curated official Qwen3 GGUF profiles under Apache-2.0: 0.6B Q8_0, 1.7B Q8_0, and the recommended 4B Q4_K_M. Exact artifact size, SHA-256, source, and license metadata are verified before activation. No translation model downloads automatically. Synthetic English↔Russian testing rejected 0.6B and 1.7B as quality defaults; the 4B profile produced materially more natural dialogue while preserving every ID and timestamp. The main translation flow remains disabled until representative real-subtitle samples and output modes have been evaluated.

Real-SRT testing also found that a small model can assign the same long translation to two different source cues inside a larger batch even while returning valid IDs. The service therefore treats a long duplicate translation for different source text as suspicious and retries only those cues individually with neighbouring context. Identical source dialogue and short conversational repetitions do not trigger the retry. Allowed target IDs are embedded directly in the generation schema in addition to the service-side missing/duplicate/invented-ID checks.

### Context batches

The default batch contains 12 cues to translate, plus up to two context-only cues before and after it. Context-only cues are visible to the model but forbidden in its result. This keeps requests bounded while preserving dialogue context across batch boundaries.

### Structural safeguards

- Cue text is serialized as data and explicitly treated as untrusted quoted dialogue, not as model instructions.
- llama.cpp JSON-schema generation constrains the response shape.
- Every requested ID must occur exactly once.
- Context-only, unknown, duplicate, and missing IDs abort the operation.
- Original IDs, timestamps, and transcript text remain unchanged.
- Source-language word timings are removed from translated cues because those token positions do not describe translated words.
- Cancellation is checked between batches and progress is monotonic.

### Output policy

The completed UI will offer three explicit choices:

1. original SRT only;
2. translated SRT, with optional preservation of the original SRT;
3. optional bilingual SRT containing both texts.

Translation happens after recognition and review/correction. Existing files remain non-overwriting.

## Acceptance criteria before enabling translation in the main UI

- [complete] Managed llama.cpp and translation-model installation with trusted upstream URLs, exact checksums, license text, and visible disk size.
- A real English→Russian film sample and a Russian→English sample complete locally without losing or inventing IDs.
- Names, numbers, negation, sentence fragments, and dialogue spanning batch boundaries are included in the evaluation set.
- Cancellation, malformed model output, and insufficient disk space have user-facing errors.
- Original, translated, and bilingual exports are validated in common SRT players.
- Translation quality and runtime are shown honestly; the UI does not call the feature ready merely because the process returns JSON.

## Consequences

The repository now contains the tested batching, stable-ID contract, strict result validation, and a local llama.cpp engine adapter. The feature remains off the main screen until the managed component/model and real-media acceptance checks are complete. Dedicated machine-translation engines such as Marian/OPUS-MT can be added behind the same interface later if their packaging and model licenses are preferable.
