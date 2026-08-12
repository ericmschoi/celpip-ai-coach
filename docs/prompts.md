# Prompts and structured-output schemas

Every prompt is versioned. When wording changes, bump the version constant in code **and** the entry
here, so a stored exercise can always be traced back to what produced it. The version is recorded on
each exercise as `sourceRef`, for example `model:gpt-5.6-luna/listening-generation-v1`.

Source of truth: [`ListeningPrompts.java`](../backend/src/main/java/com/listenspeak/coach/listening/generation/ListeningPrompts.java).
This document mirrors it; if they disagree, the code is right.

---

## Listening generation — `listening-generation-v1`

**Model:** `OPENAI_GENERATION_MODEL` (default `gpt-5.6-luna`)
**API:** Responses API, `text.format` = `json_schema`, `strict: true`, `store: false`
**Max output tokens:** 8000

### System prompt

Establishes the non-negotiables:

- Everything original; never reproduce, paraphrase, or imitate real test material.
- Never describe the output as official, certified, accredited, or a real test.
- Dialogue must sound spoken: contractions, interruptions, someone changing their mind.
- **Turn text is spoken verbatim by a voice model**, so it must contain no speaker name, colon
  prefix, stage direction, or sound effect.
- Distractors plausible to a half-listener, unambiguously wrong to a careful one. Never two
  defensible options.
- Each question's `evidence` must quote or closely paraphrase words that appear in its own
  `speakerTurns`.
- Neutral, respectful content. No real living people, no politics, no medical or legal advice.

### User prompt

Assembled from the requested part and difficulty:

| Slot                  | Source                                                    |
| --------------------- | --------------------------------------------------------- |
| Part number and label | `Part.label()`                                            |
| Style profile         | `Part.profile()`                                          |
| Speaker count         | `Part.speakerCount()` — 1 for Part 4, 3 for Part 5, else 2 |
| Turn range            | `Part.minTurns()`–`Part.maxTurns()`                       |
| Difficulty guidance   | see below                                                  |
| Skill list            | every value of the `Skill` enum                            |

It also demands: exactly 6 questions, exactly 4 options each, at least four different skills, at
least one question requiring two statements to be combined, and at least one about what a speaker
decides or how they feel.

### Difficulty guidance

| Level        | Instruction given to the model                                                                                                    |
| ------------ | -------------------------------------------------------------------------------------------------------------------------------- |
| `DEVELOPING` | Everyday vocabulary, short sentences, one idea per turn; answers stated fairly directly, though not in the option's exact words.   |
| `COMPETENT`  | Natural pace and some idiom; speakers qualify statements and talk past each other; several answers need paraphrase or inference.   |
| `ADVANCED`   | Dense, idiomatic speech with implication and hedging; speakers concede and revise; most answers need inference or position-tracking. |

These are authoring guides, not calibrated levels, and the UI says so.

### JSON schema

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["title", "scenario", "speakerTurns", "questions", "listeningTip"],
  "properties": {
    "title": { "type": "string" },
    "scenario": { "type": "string" },
    "listeningTip": { "type": "string" },
    "speakerTurns": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["speakerId", "speakerDisplayName", "text", "pauseAfterMs"],
        "properties": {
          "speakerId": { "type": "string" },
          "speakerDisplayName": { "type": "string" },
          "text": { "type": "string" },
          "pauseAfterMs": { "type": "integer" }
        }
      }
    },
    "questions": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["id", "stem", "options", "correctOptionId", "explanation", "evidence", "skill"],
        "properties": {
          "id": { "type": "string" },
          "stem": { "type": "string" },
          "correctOptionId": { "type": "string", "enum": ["A", "B", "C", "D"] },
          "explanation": { "type": "string" },
          "evidence": { "type": "string" },
          "skill": {
            "type": "string",
            "enum": ["DETAIL","PURPOSE","SPEAKER_IDENTIFICATION","PARAPHRASE","INFERENCE","ATTITUDE","FINAL_POSITION"]
          },
          "options": {
            "type": "array",
            "items": {
              "type": "object",
              "additionalProperties": false,
              "required": ["id", "text"],
              "properties": {
                "id": { "type": "string", "enum": ["A", "B", "C", "D"] },
                "text": { "type": "string" }
              }
            }
          }
        }
      }
    }
  }
}
```

Strict mode requires every property in `required` and `additionalProperties: false` on every object,
which is why nothing here is optional.

### Server-side validation

The schema guarantees shape. It cannot guarantee sense, so
[`ExerciseValidator`](../backend/src/main/java/com/listenspeak/coach/listening/domain/ExerciseValidator.java)
additionally rejects:

| Rule                                  | Why                                                              |
| ------------------------------------- | ---------------------------------------------------------------- |
| Exactly 6 questions                   | The MVP's fixed set size                                          |
| Exactly 4 options, ids A–D, all unique | A duplicated distractor makes a question unanswerable            |
| `correctOptionId` names a real option | Otherwise scoring has no answer                                   |
| Evidence words occur in the transcript | Catches evidence the model invented                              |
| No duplicate question stems           | Six questions must test six things                                |
| At least three distinct skills        | Prevents six literal-recall questions                             |
| Correct speaker count for the part     | Part 5 needs three voices for its "who said it" questions         |
| Every speaker has ≥2 turns            | A speaker with one line cannot be tracked                         |
| Consistent id → display-name mapping   | Otherwise transcript and voice assignment disagree                |
| No turn text starting with a label     | `"Elena:"` would be read aloud by the voice                       |
| 150–900 transcript words, turns in range | Too short to support six questions, or implausibly long        |
| No self-description as official        | Legal and honesty requirement                                     |

### Retry policy

On failure, **exactly one** retry, with the collected validation errors appended to the original
prompt. If the second attempt also fails, the request returns `GENERATION_INVALID` and stops. There
is no unbounded loop against a paid API.

---

## Text-to-speech instructions

**Model:** `OPENAI_TTS_MODEL` (default `gpt-4o-mini-tts`), `response_format: wav`

The same instruction string accompanies every turn:

> Speak in natural Canadian English at a normal conversational pace, as if recorded in a quiet studio
> with a good microphone. Sound like a real person talking to someone in the room, not like a
> narrator reading aloud. Do not add music, sound effects, background noise, hum, ringing, echo, or
> any introduction. Do not announce or read out any speaker name, label, or stage direction. Speak
> only the words given.

The `input` is exactly the turn's `text` — never a label, never a name. See
[audio-quality.md](audio-quality.md) for voices and assembly.

---

## Speaking prompt generation — `speaking-prompt-v1`

**Model:** `OPENAI_GENERATION_MODEL` · **Max output tokens:** 800
**Source:** [`SpeakingPrompts.java`](../backend/src/main/java/com/listenspeak/coach/speaking/prompts/SpeakingPrompts.java)

The system prompt requires original content, concrete everyday Canadian settings, and a scenario
answerable by anyone without special knowledge or a particular job or family situation. It
explicitly forbids stating a time limit: **timings never come from the model.** They come from
`SpeakingTaskCatalog` and are attached to the prompt server-side, so a model cannot shorten or
lengthen the exercise.

Schema: `{ situation, instruction, bullets[] }`, all required, `additionalProperties: false`.

In demo mode the eight prompts in
[`seed/speaking/prompts.json`](../backend/src/main/resources/seed/speaking/prompts.json) are served
instead, so the whole flow works with no key.

## Speaking evaluation — `speaking-scoring-v1`

**Model:** `OPENAI_SCORING_MODEL` (default `gpt-5.6-terra`) · **Max output tokens:** 4000
**Source:** [`ScoringPrompts.java`](../backend/src/main/java/com/listenspeak/coach/speaking/evaluation/ScoringPrompts.java)

The model receives the task, the prompt, the transcript, the time limit, and the locally computed
delivery metrics. **It never receives the audio.** The system prompt therefore states:

- Ground every judgement in the supplied transcript or metrics, quoting the speaker's own words.
- **You cannot hear pronunciation, accent, intonation, or voice quality. Never comment on them, and
  never claim a word was mispronounced.** Listenability is judged from pace, pausing, hesitation,
  and repetition — things the metrics actually show — and from whether the wording is easy to follow.
- Where the transcript is ambiguous, say so rather than guessing.
- Judge language, not opinions: a well-argued view the model disagrees with scores well.
- Score conservatively; prefer lower confidence to a confident middling score.
- Corrections must preserve the speaker's meaning.
- The sample answer must be one clear step up from what they produced, not a model essay.

An unofficial 1–12 band guide is included, with each band described by what a listener experiences.

### Delivery metrics supplied as evidence

Computed locally by FFmpeg and `DeliveryMetrics`, never by the model:

```
duration: 78.4s of 90s allowed (87% of the time used)
words: 168
pace: 142 words per minute while speaking
fillers: 5
repeated word starts: 2
silence: 12% of the recording, longest pause 2.1s
```

Pace is computed over *speaking* time, not wall-clock time, so a long pause does not make someone
look slow.

### Schema

`estimatedLevel` (1–12), `confidence` (LOW/MEDIUM/HIGH), `dimensions[]` (each with an enum
dimension, a 1–12 score, and evidence), `strengths[]`, `improvements[]` (issue / whyItMatters /
howToFix), `corrections[]` (original / improved / reason), `sampleAnswer`, `nextDrill`. Every
property required, `additionalProperties: false` throughout.

### Server-side validation

[`ScoreGuard`](../backend/src/main/java/com/listenspeak/coach/speaking/evaluation/ScoreGuard.java)
runs on every assessment, whatever produced it:

| Rule                                                      | Why                                                     |
| --------------------------------------------------------- | ------------------------------------------------------- |
| Overall level clamped to 1–12                              | A schema minimum is not a guarantee                      |
| Each dimension score clamped to 1–12                       | Same                                                     |
| All four dimensions present exactly once                   | A missing dimension would silently disappear from the UI |
| Duplicate dimension: first score wins                      | Contradictory second opinions are dropped                |
| Overall level within 2 of the dimension mean               | The headline number must be explainable by its own parts |
| Blank evidence replaced, never shown empty                 | An empty evidence field looks like a bug                 |
| Missing sample answer or next drill rejected               | Those are the actionable half of the feedback            |

### Never speaking for the user

A transcript is a claim about what someone said, so it may only ever contain words they actually
said. Three rules enforce this:

1. **`SeedTranscriber` returns an empty string.** It cannot hear, so it reports nothing. An earlier
   version returned a fixed sample answer, which the results screen then displayed under "What we
   heard" — including when the user had recorded silence. That was fabrication and is now covered by
   tests on both sides.
2. **Silent recordings are refused before anything is transcribed or scored.** `SpeechPresence`
   rejects a recording that is at least 95% silence or holds under 1.5 seconds of audible sound. In
   `LIVE` mode this also saves a provider call.
3. **Absence is representable.** `estimatedLevel` and each dimension score are nullable. When no
   transcription is available, demo mode reports Content/Coherence and Vocabulary as *not assessed*,
   gives no overall level, and returns no corrections, rather than deriving numbers from delivery
   metrics and presenting them as language judgements. `LIVE` scoring refuses outright to score an
   empty transcript.

In demo mode, `SeedSpeakingScorer` scores only what was genuinely measured from the user's own audio
— use of the time and pausing — always reports `LOW` confidence, and labels its sample answer as a
general example rather than a rewrite of an answer nothing has read.

---

## Changing a prompt

1. Edit `ListeningPrompts.java`.
2. Bump `VERSION` (`listening-generation-v1` → `-v2`).
3. Update this document.
4. Run `make test` — the validator tests are the guard rail that catches a prompt change which
   quietly stops producing valid exercises.
