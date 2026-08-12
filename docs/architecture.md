# Architecture

## System

```mermaid
flowchart LR
  subgraph Browser["Browser (untrusted)"]
    SPA["React + TypeScript SPA"]
  end

  subgraph AWS["AWS ca-central-1"]
    CF["CloudFront (OAC)"]
    WEB[("S3: static bundle<br/>private")]
    ALB["Application Load Balancer"]
    API["ECS Fargate<br/>Spring Boot + FFmpeg"]
    DDB[("DynamoDB<br/>single table")]
    AUDIO[("S3: audio<br/>private, TTL")]
    SM["Secrets Manager<br/>OpenAI key"]
    COG["Cognito User Pool<br/>self-signup disabled"]
    LOGS["CloudWatch Logs"]
  end

  OPENAI["OpenAI API"]

  SPA -->|"static"| CF --> WEB
  SPA -->|"Bearer JWT"| ALB --> API
  SPA -.->|"Authorization Code + PKCE"| COG
  API -->|"verify JWT (JWKS)"| COG
  API --> DDB
  API --> AUDIO
  API -->|"read at startup"| SM
  API -->|"generation, TTS, transcription, scoring"| OPENAI
  API --> LOGS
  SPA -.->|"presigned GET, short TTL"| AUDIO
```

## Trust boundaries

| Boundary                | What crosses it                                      | Control                                                                  |
| ----------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------ |
| Browser → API           | User answers, recordings, task selections            | Cognito JWT, strict CORS allowlist, bean validation, per-user rate limits |
| API → OpenAI            | Prompts, transcripts, recordings                      | Server-side key from Secrets Manager, timeouts, bounded retries           |
| API → S3/DynamoDB       | Exercises, evaluations, audio                         | Least-privilege task role scoped to one table and one bucket prefix       |
| Browser → S3 (audio)    | Generated audio bytes                                 | Presigned GET only, short expiry, issued to the authenticated owner       |
| Internet → Fargate task | Nothing                                               | Security group permits inbound only from the ALB security group           |

The browser never holds a provider key, never receives another user's data, and never receives an
answer key before submission.

## Request flows

### Listening: generate → practise → submit

```mermaid
sequenceDiagram
  autonumber
  participant U as Browser
  participant A as API
  participant O as OpenAI
  participant D as DynamoDB
  participant S as S3

  U->>A: POST /listening/exercises {part, difficulty}
  A->>A: rate limit + daily cap for this user
  alt SEED mode
    A->>A: load deterministic fixture
  else LIVE mode
    A->>O: Responses API, strict JSON schema
    O-->>A: structured exercise
    A->>A: validate (6 questions, 4 unique options, answer supported, no leakage)
    Note over A,O: one retry with the validation errors, then a stable error
    A->>O: TTS per speaker turn, bounded concurrency
    O-->>A: WAV segments
    A->>A: FFmpeg assemble, normalize, audio QA
    A->>S: put private object
  end
  A->>D: persist the complete exercise (PK=USER#sub)
  A-->>U: ExercisePublicView + presigned audio URL
  Note right of A: no speakerTurns, no correctOptionId,<br/>no explanation, no evidence

  U->>A: POST /listening/exercises/{id}/submissions
  A->>D: load exercise, owner-scoped
  A->>A: score deterministically
  A->>D: persist attempt
  A-->>U: score, per-question result, rationale, evidence, transcript, one tip
```

### Speaking: record → transcribe → evaluate

```mermaid
sequenceDiagram
  autonumber
  participant U as Browser
  participant A as API
  participant O as OpenAI
  participant D as DynamoDB

  U->>A: POST /speaking/tasks/{n}/prompts
  A-->>U: original prompt + timings from the central catalog
  U->>U: prepare countdown, then record (MediaRecorder)
  U->>A: POST /speaking/evaluations (multipart audio)
  A->>A: content type, size, duration checks
  A->>O: transcription
  O-->>A: transcript
  A->>A: FFmpeg delivery metrics (duration, WPM, fillers, silence)
  A->>O: scoring, strict JSON schema, evidence-grounded
  O-->>A: four dimension scores + feedback
  A->>A: clamp and validate every score server-side
  A->>D: persist evaluation
  A->>A: delete the recording unless retention is enabled
  A-->>U: estimate, confidence, evidence, strengths, improvements, sample answer, drill
```

## Data design

Single table, on-demand billing, TTL attribute `expiresAt`.

| Item                | PK             | SK                                            | TTL |
| ------------------- | -------------- | --------------------------------------------- | --- |
| Listening exercise  | `USER#{sub}`   | `EXERCISE#{exerciseId}`                       | yes |
| Listening attempt   | `USER#{sub}`   | `LISTENING_ATTEMPT#{createdAt}#{attemptId}`   | no  |
| Speaking evaluation | `USER#{sub}`   | `SPEAKING_EVALUATION#{createdAt}#{evalId}`    | no  |
| Daily usage counter | `USER#{sub}`   | `USAGE#{yyyy-mm-dd}`                          | yes |

Every access pattern is a single-partition query. Binary audio is never stored in DynamoDB; the
item holds only the S3 key.

## Data lifecycle

| Data                  | Where                | Retention                                            |
| --------------------- | -------------------- | ---------------------------------------------------- |
| Generated exercise    | DynamoDB             | TTL, default 30 days                                  |
| Generated audio       | S3 `listening/`      | Lifecycle expiry, 7 days in dev                       |
| Speaking recording    | S3 `speaking/`       | Deleted after evaluation by default; 1-day lifecycle as a backstop |
| Transcript + feedback | DynamoDB             | Kept until the user deletes it                        |
| Local temp files      | container filesystem | Deleted in a `finally` block, always                  |

## Key trade-offs

**No NAT Gateway.** A NAT Gateway is roughly the cost of the rest of this stack combined, purely to
give one container outbound access to OpenAI. Instead the Fargate task runs in a public subnet with
a public IP for egress, while its security group accepts inbound traffic only from the ALB security
group. The container is not reachable from the internet; it is only able to reach out. If this app
ever grew a compliance requirement for fully private subnets, the fix is a NAT Gateway or VPC
endpoints, and the cost changes accordingly.

**Synchronous listening generation first.** Generation plus TTS plus assembly is the slowest path in
the app. It starts synchronous because that is far simpler, and it is refactored into a
`PENDING → GENERATING → READY → FAILED` job with client polling only if measured latency actually
exceeds a practical HTTP timeout. No queue and no second always-on worker is introduced on
speculation.

**Single stack.** See ADR-007.

**SEED mode everywhere.** See ADR-005. It is why CI, e2e, and a fresh clone all work with no API key.
