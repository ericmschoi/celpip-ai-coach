# API

Base path: `/api/v1`. All timestamps are UTC ISO-8601 instants. All identifiers are UUIDs.

## Generated specification

The OpenAPI document is produced from the running application, so it cannot drift from the code:

- JSON: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

Write it to a file with:

```bash
curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool > docs/openapi.json
```

## Authentication

Every route under `/api/v1` requires authentication. `/actuator/health` and the OpenAPI routes are
public.

| Mode         | How the caller authenticates                                            |
| ------------ | ----------------------------------------------------------------------- |
| `COGNITO`    | `Authorization: Bearer <Cognito access token>`                          |
| `LOCAL_STUB` | `X-Dev-User: <any id>` — **development and e2e only**, never deployed   |

`LOCAL_STUB` is registered as a filter only when `app.auth.mode=LOCAL_STUB`, and the application
logs a warning at startup when it is active.

## Errors

Every failure is [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details with two
extensions: a stable `code` the client branches on, and a `retryable` hint.

```json
{
  "type": "https://listenspeak.app/problems/daily-limit-reached",
  "title": "Daily practice limit reached",
  "status": 429,
  "detail": "You have used all 20 listening generations today.",
  "instance": "/api/v1/listening/exercises",
  "code": "DAILY_LIMIT_REACHED",
  "retryable": false
}
```

Validation failures add an `errors` array of `{field, message}`.

| `code`                    | Status | Retryable | Meaning                                        |
| ------------------------- | ------ | --------- | ---------------------------------------------- |
| `VALIDATION_FAILED`       | 400    | no        | Request body or parameter is invalid            |
| `UNAUTHORIZED`            | 401    | no        | Missing or invalid token                        |
| `FORBIDDEN`               | 403    | no        | Authenticated but not permitted                 |
| `NOT_FOUND`               | 404    | no        | Unknown route, or a resource you do not own     |
| `ALREADY_SUBMITTED`       | 409    | no        | The exercise was already submitted              |
| `UNSUPPORTED_MEDIA_TYPE`  | 415    | no        | Audio format not accepted                       |
| `PAYLOAD_TOO_LARGE`       | 413    | no        | Recording exceeds the configured limit          |
| `RATE_LIMITED`            | 429    | yes       | Per-minute burst limit                          |
| `DAILY_LIMIT_REACHED`     | 429    | no        | Configured daily cap for this user              |
| `PROVIDER_REFUSED`        | 422    | no        | The provider declined on content grounds        |
| `GENERATION_INVALID`      | 422    | yes       | Generated exercise failed server validation     |
| `AUDIO_QUALITY_FAILED`    | 422    | yes       | Assembled audio failed QA                       |
| `PROVIDER_RATE_LIMITED`   | 503    | yes       | Upstream rate limit                             |
| `PROVIDER_UNAVAILABLE`    | 503    | yes       | Upstream outage                                 |
| `PROVIDER_NOT_CONFIGURED` | 503    | no        | `LIVE` mode without an API key                  |
| `PROVIDER_TIMEOUT`        | 504    | yes       | Upstream timed out                              |
| `INTERNAL_ERROR`          | 500    | yes       | Unexpected failure; details are logged, not returned |

Cross-user access deliberately returns `404`, not `403`, so probing cannot confirm that another
user's resource exists.

## Idempotency

`POST` routes that can incur provider cost accept an `Idempotency-Key` header. Repeating a request
with the same key returns the original result instead of generating and charging again.

## Endpoints

### Implemented

| Method | Path                  | Description                                            |
| ------ | --------------------- | ------------------------------------------------------ |
| `GET`  | `/api/v1/config`      | Client bootstrap: content mode, auth mode, parts, speaking tasks with timings, difficulties, daily limits |
| `POST` | `/api/v1/listening/exercises` | Generate an exercise. Accepts `Idempotency-Key`. Rate limited and daily capped. |
| `GET`  | `/api/v1/listening/exercises/{exerciseId}` | Fetch an exercise you own, without answers |
| `POST` | `/api/v1/listening/exercises/{exerciseId}/submissions` | Score an attempt and reveal the transcript |
| `GET`  | `/api/v1/speaking/tasks` | The eight tasks with their preparation and answer times |
| `POST` | `/api/v1/speaking/tasks/{taskNumber}/prompts` | Generate an original prompt |
| `POST` | `/api/v1/speaking/evaluations?promptId=…` | `multipart/form-data`, part name `recording` |
| `GET`  | `/api/v1/speaking/evaluations/{evaluationId}` | Fetch an evaluation you own |
| `GET`  | `/actuator/health`    | Liveness/readiness for the load balancer (public)      |

`GET /api/v1/config` returns non-sensitive values only. It is covered by a test asserting that no
provider or infrastructure identifier appears in the response.

```json
{
  "contentMode": "SEED",
  "authMode": "LOCAL_STUB",
  "listeningParts": [1, 2, 3, 4, 5, 6],
  "speakingTasks": [
    {
      "taskNumber": 1,
      "title": "Giving Advice",
      "focus": "Advise one person about a specific decision, with reasons.",
      "preparationSeconds": 30,
      "answerSeconds": 90
    }
  ],
  "difficulties": ["DEVELOPING", "COMPETENT", "ADVANCED"],
  "dailyLimits": { "listening": 20, "speaking": 30 }
}
```

Task timings live only in `SpeakingTaskCatalog` on the server and reach the client through this
endpoint, so no component hard-codes a duration.

### Planned

_All planned endpoints are now implemented._

The Listening read model has two distinct types. `ExercisePublicView`, returned before submission,
has no `speakerTurns`, `correctOptionId`, `explanation`, or `evidence` **fields**. The full
transcript, answer key, and rationale appear only in the submission response.

## Listening request and response examples

### `POST /api/v1/listening/exercises`

```json
{ "part": 5, "difficulty": "COMPETENT" }
```

`201 Created`, `Location: /api/v1/listening/exercises/{id}`:

```json
{
  "id": "0b0a…",
  "part": 5,
  "partLabel": "Discussion",
  "difficulty": "COMPETENT",
  "title": "Registered Courses or Drop-In Classes",
  "scenario": "Three members of a community centre's programming committee meet…",
  "speakers": ["Priya", "Dale", "Marcus"],
  "questionCount": 6,
  "questions": [
    {
      "id": "q1",
      "stem": "According to Dale, what has to happen before a registered course will run?",
      "options": [
        { "id": "A", "text": "…" },
        { "id": "B", "text": "…" },
        { "id": "C", "text": "…" },
        { "id": "D", "text": "…" }
      ]
    }
  ],
  "audioUrl": "/media/listening?token=…",
  "audioDurationSeconds": 180,
  "audioDisclosure": "This exercise uses AI-generated voices.",
  "createdAt": "2026-08-11T19:02:11Z"
}
```

There is no `speakerTurns`, `correctOptionId`, `explanation`, or `evidence` **field on this type**,
so there is nothing to omit at serialization time. Two backend tests and one e2e test assert their
absence.

`audioUrl` is short-lived: an S3 presigned GET in AWS, or a signed local `/media/listening` link in
development. Both expire after `APP_PRESIGNED_URL_TTL` (default 15 minutes).

### `POST /api/v1/listening/exercises/{exerciseId}/submissions`

```json
{
  "answers": [
    { "questionId": "q1", "selectedOptionId": "B" },
    { "questionId": "q2", "selectedOptionId": "C" }
  ]
}
```

Every question must be answered exactly once with an option that exists on it; anything else is
`VALIDATION_FAILED`. A second submission for the same exercise is `ALREADY_SUBMITTED`.

`200 OK` returns `correctCount`, `totalQuestions`, `scorePercent`, a `results` array with
`correctOptionId`, `correctOptionText`, `explanation`, `evidence` and `skill` per question, the full
`transcript`, `weakestSkill`, and one `tip` chosen from the skill the user missed most.

## Cost controls

`POST /api/v1/listening/exercises` is limited twice per user: a burst limit
(`APP_LIMITS_BURST_PER_MINUTE`, default 5/min) returning `RATE_LIMITED`, and a daily cap
(`APP_LIMITS_LISTENING_PER_DAY`, default 20) returning `DAILY_LIMIT_REACHED`. A replayed
`Idempotency-Key` returns the original exercise without consuming allowance.

**Known limitation:** the burst limiter and the idempotency map are in-process. With the single
Fargate task this app deploys that is exact; with more than one task each gets its own. The daily cap
goes through a durable counter and stays correct either way.
