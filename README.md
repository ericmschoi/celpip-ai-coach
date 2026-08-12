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

## Status

| Phase                            | State          |
| -------------------------------- | -------------- |
| 1 — Foundation                   | ✅ done        |
| 2 — Listening vertical slice     | ✅ done        |
| 3 — Speaking vertical slice      | ⬜ not started |
| 4 — Private AWS deployment       | ⬜ not started |
| 5 — Handoff                      | ⬜ not started |

What works today: the full Listening flow, end to end. Pick a part and difficulty, get an original
exercise with multi-voice audio, answer six questions, submit, and see the score, per-question
rationale with evidence, the full transcript, and one targeted tip. It runs with no API key at all
in demo mode, and with real generation, TTS, and audio assembly when `OPENAI_API_KEY` is set.
Speaking practice lands in phase 3.

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
                   ├── OpenAI  (generation, TTS, transcription, scoring)
                   ├── DynamoDB (single table: exercises, attempts, evaluations, usage)
                   ├── S3       (generated audio, temporary uploads, presigned GET only)
                   └── Secrets Manager (OpenAI API key)
```

The browser never talks to OpenAI. Answers and transcripts never leave the server before
submission. See [docs/architecture.md](docs/architecture.md) for request flows and trust boundaries.

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
| `OPENAI_TRANSCRIPTION_MODEL`| `gpt-transcribe`        | Speaking transcription                              |
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

Not yet enabled — phase 4. When it lands, `docs/aws-runbook.md` will cover deploy, private user
creation, secret rotation, log access, budget alerts, and teardown. Nothing in this repository
creates billable resources until you explicitly run `cdk deploy` and approve the cost summary.

Planned recurring cost categories for a personal `dev` environment: ECS Fargate task hours
(the dominant cost), Application Load Balancer hours, CloudFront and S3 request/storage,
DynamoDB on-demand requests, CloudWatch log ingestion, Secrets Manager secret-months, and
OpenAI usage (billed separately by OpenAI, not AWS).

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
