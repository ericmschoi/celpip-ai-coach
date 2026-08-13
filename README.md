# ListenSpeak AI Coach

Practice smarter. Speak better. Score higher.

An independent, personal web app for **CELPIP-style Listening and Speaking practice**. Every
exercise is generated originally by an AI pipeline: multi-speaker listening sets with synthesized
audio, and speaking tasks that are recorded in the browser, transcribed, and scored against four
dimensions.

> **This product is independent.** It is not affiliated with, authorized by, or endorsed by CELPIP
> or Paragon Testing Enterprises. No official test questions, recordings, images, or study material
> are used or reproduced. Difficulty labels describe style complexity only, and any level estimate
> is an AI approximation, not an official score.

---

> **Provider status:** the OpenAI transcription and scoring steps have **never been called**. They
> are implemented and covered by mocked tests, which proves the wiring and nothing about accuracy.
> See [docs/live-verification.md](docs/live-verification.md) for the one controlled run that closes
> that gap. Listening generation and TTS are in the same position.

## Status

| Phase                            | State          |
| -------------------------------- | -------------- |
| 1 — Foundation                   | ✅ done        |
| 2 — Listening vertical slice     | ✅ done        |
| 3 — Speaking vertical slice      | ✅ done        |
| 4 — Private AWS deployment       | 🟡 built, not deployed |
| 5 — Handoff                      | ⬜ not started |

What works today: the full Listening flow, end to end. Pick a part and difficulty, get an original
exercise with multi-voice audio, answer six questions, submit, and see the score, per-question
rationale with evidence, the full transcript, and one targeted tip. It runs with no API key at all
in demo mode, and with real generation, TTS, and audio assembly when `OPENAI_API_KEY` is set.
Speaking works too: pick one of the eight tasks, get an original prompt with its real preparation
and answer times, record in the browser with auto-stop at the limit, and get an unofficial 1-12
estimate with four dimension scores, evidence quoted from your own words, corrections, a stronger
sample answer, and a next drill.

## Screenshots

_Placeholder — added once the Listening player and Speaking recorder exist (phase 2 and 3)._

## Architecture at a glance

```
Browser (React + TypeScript SPA)
    │  HTTPS, Bearer token
    ▼
CloudFront ──► S3 (private, OAC)          static bundle
    │
    └──► ALB ──► ECS Fargate (Spring Boot + FFmpeg)
                   ├── OpenAI  (generation, TTS, transcription, timing, scoring)
                   ├── DynamoDB (single table: exercises, attempts, evaluations, usage)
                   ├── S3       (generated audio, temporary uploads, presigned GET only)
                   └── Secrets Manager (OpenAI API key)
```

The browser never talks to OpenAI. Answers and transcripts never leave the server before
submission. See [docs/architecture.md](docs/architecture.md) for request flows and trust boundaries.

## AI models

Every model name is configuration, overridable by environment variable without a code change. The
browser never calls a provider: all traffic goes through the Spring backend.

| Purpose | Default model | Env var | Endpoint | Why this one |
| --- | --- | --- | --- | --- |
| Listening exercise generation | `gpt-5.6-luna` | `OPENAI_GENERATION_MODEL` | Responses API | Structured Outputs with a strict JSON schema |
| Listening voices | `gpt-4o-mini-tts` | `OPENAI_TTS_MODEL` | `/audio/speech` | Per-speaker voices, WAV output for lossless assembly |
| Speaking transcript | `gpt-transcribe` | `OPENAI_TRANSCRIPTION_MODEL` | `/audio/transcriptions` | Best transcript quality; accepts `prompt`, `keywords`, `languages` |
| Speaking word timings | `whisper-1` | `OPENAI_TIMING_MODEL` | `/audio/transcriptions` | The **only** model supporting `timestamp_granularities` and `verbose_json` |
| Speaking evaluation | `gpt-5.6-terra` | `OPENAI_SCORING_MODEL` | Responses API | Structured Outputs with a strict JSON schema |

**Why two models transcribe the same audio.** `timestamp_granularities` and `verbose_json` are
supported only by `whisper-1`, while `gpt-transcribe` is the better transcriber and supports
`keywords` and `languages` (plural). Rather than send unsupported parameters and recover from the
error, an explicit capability map in `TranscriptionModels` decides what each model may receive. The
`gpt-transcribe` text is what you see and what Content, Vocabulary, and Task Fulfillment are scored
from; the `whisper-1` output is used for word timings only and is never shown.

A full Speaking evaluation is therefore **three provider requests**. Setting `OPENAI_TIMING_MODEL=`
(empty) reduces it to two and drops only the timestamp-derived measurements.

### What is never claimed

- **No pronunciation or accent judgement.** The scoring model never hears the audio, so the prompt
  forbids any comment on pronunciation, accent, intonation, or voice quality.
- **No confidence score.** `gpt-transcribe` returns no logprobs, and whisper's `avg_logprob` is not
  calibrated against labelled human transcripts. It is kept as an internal diagnostic and never
  converted into a percentage or shown as speaking quality.
- **No invented values.** When transcription or timing is unavailable, the affected measurements are
  reported as unavailable, never as zero and never inferred.

## How it works

### Listening: generation to a scored attempt

```
POST /listening/exercises
  │
  ├─ SEED mode ──► committed fixture (no provider call, no cost)
  │
  └─ LIVE mode
       ├─ 1. gpt-5.6-luna, strict JSON schema, store=false
       ├─ 2. ExerciseValidator: 6 questions x 4 unique options, one supportable
       │     answer each, evidence present in the transcript, ≥3 distinct skills,
       │     right speaker count, no spoken speaker labels, not self-described
       │     as official
       │     └─ on failure: exactly ONE retry carrying the validation errors,
       │        then a stable GENERATION_INVALID. No unbounded retry loop.
       ├─ 3. gpt-4o-mini-tts per turn, stable first-appearance voice per speaker
       │     (marin, cedar, sage), max 4 requests in flight
       ├─ 4. FFmpeg assembly strictly by turn index, generated silence between
       │     turns, loudnorm to -18 LUFS, limiter, 24 kHz mono MP3
       ├─ 5. Audio QA: size, decodability, duration, clipping, dead air
       │     └─ on failure: AUDIO_QUALITY_FAILED, nothing is stored
       └─ 6. Private S3, short-lived presigned GET to the owner only
```

The response before submission is a **separate type** with no `speakerTurns`, `correctOptionId`,
`explanation`, or `evidence` field. Secrecy is a property of the type, not a serializer setting.
Scoring is deterministic and happens on the server; the client sends only option ids.

### Speaking: recording to evaluation

```
browser MediaRecorder (webm/opus, capability-detected, auto-stop at the limit)
  │
  ▼
POST /speaking/evaluations  (multipart)
  ├─ 1. Upload validation: content-type allowlist, size cap, UUID temp path,
  │     client filename ignored entirely
  ├─ 2. FFmpeg: duration, silence ratio, longest pause
  ├─ 3. SpeechPresence: a silent recording is refused here, before any spend
  ├─ 4. gpt-transcribe: verbatim prompt + filler keywords + languages=["en"]
  ├─ 5. whisper-1: word and segment timestamps (optional; failure degrades)
  ├─ 6. DeliveryMetrics: words, pace over speaking time, fillers, repeated
  │     starts, plus pause locations when timings exist
  ├─ 7. gpt-5.6-terra: task, prompt, audience and visual-context framing,
  │     verbatim transcript, time limit, metrics → four dimensions with evidence
  ├─ 8. ScoreGuard: clamps every score, requires all four dimensions, pulls the
  │     overall level back toward its own dimension mean
  └─ 9. Recording deleted in a finally block on every path
```

**Verbatim matters.** Transcription models produce readable text by default: they drop "um", merge
false starts, and tidy self-corrections. Those are exactly the tokens the filler and repetition
counts are computed from, so the request explicitly asks for them and passes the fillers as
`keywords`.

## Repository layout

```
frontend/           React + TypeScript + Vite SPA
backend/            Java 21 + Spring Boot API, packaged by feature
infra/              AWS CDK v2 (TypeScript)
docs/               architecture, api, security, prompts, audio quality, runbook
scripts/            secret scan, smoke test
.github/workflows/  CI and (manual) deployment
```

## Requirements

| Tool     | Version           | Notes                                              |
| -------- | ----------------- | -------------------------------------------------- |
| Java     | 21                | `brew install openjdk@21`; Maven comes from `mvnw` |
| Node.js  | 24 LTS            |                                                     |
| Docker   | 20.10+            | Local dependencies and the backend image            |
| FFmpeg   | 8.x               | Local audio assembly; bundled in the container      |
| AWS CLI  | v2                | Deployment only (phase 4)                           |

## Local setup

```bash
git clone https://github.com/ericmschoi/celpip-ai-coach.git
cd celpip-ai-coach
cp .env.example .env      # optional; sensible defaults work without it
make install
```

Start the local dependencies and both apps:

```bash
make deps-up
make dev
```

- Frontend: <http://localhost:5173>
- Backend: <http://localhost:8080>
- OpenAPI: <http://localhost:8080/swagger-ui.html>

The default profile runs in **SEED mode**: deterministic fixture content, no provider calls, no
cost. Set `OPENAI_API_KEY` in your shell and `APP_CONTENT_MODE=LIVE` to enable real generation.

```bash
export OPENAI_API_KEY=...      # shell only, never committed, never pasted into the app
export APP_CONTENT_MODE=LIVE
make dev-backend
```

## Environment variables

Copy [.env.example](.env.example) and fill it in. The important ones:

| Variable                    | Default                 | Purpose                                            |
| --------------------------- | ----------------------- | -------------------------------------------------- |
| `APP_CONTENT_MODE`          | `SEED`                  | `SEED` = fixtures, `LIVE` = real OpenAI calls       |
| `APP_AUTH_MODE`             | `LOCAL_STUB`            | `LOCAL_STUB` for dev/e2e, `COGNITO` when deployed   |
| `APP_STORAGE_MODE`          | `LOCAL`                 | `LOCAL` filesystem or `AWS` (DynamoDB + S3)         |
| `OPENAI_API_KEY`            | _(empty)_               | Backend environment or Secrets Manager only         |
| `OPENAI_GENERATION_MODEL`   | `gpt-5.6-luna`          | Exercise generation                                 |
| `OPENAI_SCORING_MODEL`      | `gpt-5.6-terra`         | Speaking evaluation                                 |
| `OPENAI_TTS_MODEL`          | `gpt-4o-mini-tts`       | Listening audio                                     |
| `OPENAI_TRANSCRIPTION_MODEL`| `gpt-transcribe`        | Speaking transcript                                 |
| `OPENAI_TIMING_MODEL`       | `whisper-1`             | Word timings; empty skips the request               |
| `APP_LIMITS_LISTENING_PER_DAY` | `20`                 | Cost control                                        |
| `APP_LIMITS_SPEAKING_PER_DAY`  | `30`                 | Cost control                                        |
| `APP_CORS_ALLOWED_ORIGINS`  | `http://localhost:5173` | Strict allowlist, no wildcard                       |

**Never** put a provider key in a `VITE_*` variable — those are compiled into the browser bundle.

## Commands

Every command is available from the repository root:

```bash
make test           # backend + frontend + infra tests
make lint           # oxlint, prettier check, infra typecheck
make build          # backend jar + frontend bundle
make e2e            # Playwright, SEED mode, no provider calls
make docker-build   # backend container image (includes FFmpeg)
make infra-synth    # cdk synth
make secret-scan    # tracked-file credential scan
make help           # full list
```

## AWS deployment

The infrastructure is written and `cdk synth` passes, but **nothing has been deployed**. No AWS
resource exists and nothing is being billed until you run `cdk deploy` yourself.

See [docs/aws-runbook.md](docs/aws-runbook.md) for the full procedure: bootstrap, deploy, put the
OpenAI key in Secrets Manager, create the private Cognito user, smoke test, view logs, rotate the
key, set budget alerts, and destroy everything.

```bash
make infra-synth                      # no credentials needed, creates nothing
cd infra && npx cdk diff -c env=dev   # needs credentials, still creates nothing
```

Recurring cost categories for a personal `dev` environment: ECS Fargate task hours (the dominant
cost), Application Load Balancer hours, CloudFront and S3 request/storage, DynamoDB on-demand
requests, CloudWatch log ingestion, Secrets Manager secret-months, ECR image storage, and OpenAI
usage (billed separately by OpenAI, not AWS).

## Security notes

- The OpenAI key exists only in a backend environment variable or AWS Secrets Manager.
- Cognito self-registration is disabled; this stays a private, single-user app.
- Answers, explanations, and transcripts are omitted from the pre-submission DTO on the server.
  They are not hidden with CSS or client state.
- All expensive endpoints are rate limited per authenticated user with configurable daily caps.

Full threat model: [docs/security.md](docs/security.md).

## License and content policy

Personal project. All exercises, prompts, and audio are generated originally. Do not add official
CELPIP questions, recordings, logos, or study material to this repository.
