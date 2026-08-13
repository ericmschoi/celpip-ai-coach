# Live provider verification

**Status: not yet run. No OpenAI call has ever been made by this project.**

Everything in the Speaking pipeline has been exercised with fixtures and with real FFmpeg, but the
two provider steps — transcription and scoring — have never touched OpenAI. Mocked tests prove the
wiring; they prove nothing about transcription accuracy. This document is the procedure for the one
controlled run that closes that gap.

## What is required from you

| Item | Why |
| --- | --- |
| `OPENAI_API_KEY` | The only credential needed. Shell environment only — never a file, never chat. |
| A real recording of your own speech | A synthesised recording is unrealistically clean and would prove nothing about filler retention. |
| A verbatim reference transcript, typed by hand | The accuracy figures are meaningless without a ground truth. |

The reference must preserve **fillers, repeated words, false starts, and self-corrections exactly as
spoken**. Write "um so I I think she should probably uh take it", not "So I think she should take
it."

## Environment variables

Only the first is a secret. The rest select the models and are already the defaults.

```bash
export OPENAI_API_KEY=sk-...            # required, secret
export APP_CONTENT_MODE=LIVE            # switches off fixtures
export OPENAI_TRANSCRIPTION_MODEL=gpt-transcribe   # default
export OPENAI_SCORING_MODEL=gpt-5.6-terra          # default
export OPENAI_REQUEST_TIMEOUT=120s                 # default
export OPENAI_MAX_RETRIES=2                        # default
```

Cost controls stay on: 30 speaking evaluations per day and 5 requests per minute per user.

## Step 1 — controlled accuracy test

Records the eight measurements against your reference. One transcription call and one scoring call.

```bash
export LISTENSPEAK_LIVE_TEST=true
export LIVE_RECORDING=/absolute/path/to/answer.webm
export LIVE_REFERENCE=/absolute/path/to/answer-reference.txt
export LIVE_TASK=1

cd backend && ./mvnw test -Dtest=LiveSpeakingPipelineTest
```

The test is annotated `@EnabledIfEnvironmentVariable(LISTENSPEAK_LIVE_TEST=true)`, so `make test`
and CI skip it. It prints:

1. Recognized transcript
2. Missing or incorrectly recognized words, and substitutions
3. Filler word recall
4. Repetition and self-correction preservation
5. Word timestamp availability
6. Average word confidence
7. Normalized word error rate
8. Audio format and latency, plus token usage

It fails if the word error rate exceeds 35%, if any dimension comes back unscored, or if the seed
stub is still in place.

## Step 2 — browser end to end

```bash
export OPENAI_API_KEY=sk-...
export APP_CONTENT_MODE=LIVE
make dev
```

Then at <http://localhost:5173/speaking>: pick the same task, record the same answer, submit, and
confirm the result screen shows the real transcript under "What we heard", four scored dimensions
with quoted evidence, and an estimated level. The demo-mode warning must be absent.

The full path this verifies:

```
browser recording → backend upload validation → FFmpeg measurement
  → gpt-transcribe → delivery metrics → gpt-5.6-terra → frontend result
```

## What counts as passing

- The transcript is what you actually said, fillers included.
- Word error rate under 35%, filler recall reported honestly whatever it is.
- All four dimensions scored, each quoting your words or a named measurement.
- No pronunciation or accent claim anywhere in the output.
- The Speaking result screen renders it without a demo warning.

If filler recall comes back low, the delivery metrics that depend on it (`fillerCount`,
`repeatedStarts`) are not trustworthy for that model, and the honest response is to say so in the UI
rather than keep reporting them.
