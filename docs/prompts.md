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

## Speaking evaluation

Added in phase 3.

---

## Changing a prompt

1. Edit `ListeningPrompts.java`.
2. Bump `VERSION` (`listening-generation-v1` → `-v2`).
3. Update this document.
4. Run `make test` — the validator tests are the guard rail that catches a prompt change which
   quietly stops producing valid exercises.
