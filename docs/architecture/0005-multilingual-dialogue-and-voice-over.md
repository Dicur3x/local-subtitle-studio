# ADR 0005: Multilingual dialogue, translation, and mixed voice-over

- Status: proposed; foundation only
- Date: 2026-08-23

## Context

A single audio track can contain a dominant target language, short passages in another language, or a voice-over mixed over the original performance. These are different signal problems and must not share one misleading “multilingual” switch.

whisper.cpp accepts one requested language for a transcription run. `auto` detects a language, while `--translate` translates source speech to English. It does not provide arbitrary target-language translation. Stereo diarization separates channel energy and TinyDiarize marks speaker turns; neither operation separates two voices already mixed into the same samples.

Subtitle editorial intent also matters. Foreign dialogue is not always meant to be understood. For example, Netflix's timed-text guidance says foreign dialogue should be translated when the audience was meant to understand it. Styling conventions vary by delivery specification, so italics cannot be treated as the semantic signal by itself.

## Decision

### 1. Keep the current mode explicit

The shipped MVP creates one original-language SRT from the selected audio track. Choosing a language means “prioritize and transcribe this language,” not “find and translate every language.” The UI must not claim finished mixed-language or source-separation behavior until the acceptance tests below pass.

### 2. Add three dialogue policies in a later milestone

1. **Selected language only**: transcribe the chosen language and omit confidently identified foreign speech. This is the safest default for a dubbed or voice-over track.
2. **Preserve author intent**: include foreign speech only when the source itself made it understandable. Determining this automatically is not reliable, so the first version must expose detected passages for review.
3. **All dialogue translated**: transcribe each detected language and translate foreign passages to the selected subtitle language.

Policies 2 and 3 can produce two files in one operation:

- `<video>.<target>.intent.srt`
- `<video>.<target>.all-dialogue.srt`

Existing files remain non-overwriting and gain a numeric suffix.

### 3. Separate semantics from presentation

Each recognized passage needs structured metadata before SRT rendering:

- detected language and confidence;
- original recognized text;
- optional translated text;
- whether it was included by policy or manually reviewed;
- source track and recognition pass;
- word timings and recognition confidence.

The renderer may use a configurable prefix such as `[French]` or `[французская речь]`. `<i>…</i>` is an optional presentation style because SRT player support and delivery rules vary. Language identity must not exist only as italics.

### 4. Use a staged local pipeline

The proposed mixed-language pipeline is:

```text
selected track → PCM → VAD speech regions → short-window language identification
               → merge stable language regions → language-specific Whisper passes
               → optional local translation → policy filter → review → two SRT variants
```

Short-window decisions require hysteresis and a minimum duration to avoid changing language on names, accents, or isolated loanwords. The globally selected language remains the prior probability.

### 5. Treat MVO as mixed-source audio

For MVO, the preferred inputs are separate tracks or stems when available. The app should first offer other audio tracks rather than attempting separation.

When target and original voices are mixed:

- target-language transcription is allowed with a visible “mixed voice-over” warning;
- lower original speech must not be emitted as a second confident transcript unless a separately evaluated source-separation step supplies usable stems;
- stereo diarization and speaker-turn detection must not be described as voice separation;
- every separated result must retain an uncertainty score and permit listening/review.

No source-separation dependency will be downloaded until its model license, hardware requirements, checksums, update policy, and real MVO accuracy are evaluated like the existing managed components.

### 6. Correct recognition without inventing dialogue

The current safe post-processing stage may normalize whitespace, balance line breaks, merge adjacent exact duplicates, and flag low-confidence cues. Later contextual correction may use a user glossary or a managed local language model, but must:

- retain the raw recognition text;
- preserve timestamps;
- show a before/after difference;
- never silently replace names, numbers, negation, or foreign speech;
- be optional and reproducible.

## Acceptance criteria for enabling the modes

- A labelled test set contains clean multilingual dialogue, code-switching inside one sentence, MVO over quiet originals, music, and overlapping speakers.
- Per-language regions are measured independently for language-ID accuracy, word error rate, and timing error.
- The author-intent output never invents a translation where the source intent is unknown; it asks for review.
- The all-dialogue output uses an actual target-language translation model and never presents whisper.cpp English translation as Russian output.
- MVO results are evaluated against isolated stems when available and expose failure/uncertainty.
- SRT rendering is tested in several common players for prefix and optional italic handling.

## Sources

- [whisper.cpp CLI options](https://github.com/ggml-org/whisper.cpp/blob/master/examples/cli/README.md)
- [whisper.cpp speaker segmentation notes](https://github.com/ggml-org/whisper.cpp#speaker-segmentation-via-tinydiarize-experimental)
- [Netflix Italian Timed Text Style Guide](https://partnerhelp.netflixstudios.com/hc/en-us/articles/215349898-Italian-Timed-Text-Style-Guide)

## Consequences

The current UI remains honest and simple. Mixed-language work becomes a reviewable workflow instead of an unreliable checkbox. Supporting all-dialogue Russian subtitles requires at least one new managed model family and a larger test corpus; supporting MVO may require source separation and substantially more compute.
