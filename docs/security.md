# Security

Threat model for a private, single-user application whose main assets are a paid API key, the
user's recordings, and the integrity of the practice itself.

## Assets

| Asset                       | Why it matters                                              |
| --------------------------- | ----------------------------------------------------------- |
| OpenAI API key              | Direct financial loss and reputational risk if stolen        |
| AWS credentials / task role | Full account compromise                                      |
| Speaking recordings         | The user's voice; personal data                              |
| Transcripts and evaluations | Personal data                                                |
| Answer keys and transcripts | Practice is worthless if they leak before submission         |
| Compute and provider budget | An open endpoint is a bill someone else can run up           |

---

## T1 — API key theft

**Vectors:** committed to git, baked into the frontend bundle, echoed in logs, returned in an error
body, exposed via a debug endpoint.

**Controls**

- The key is read from the backend environment or Secrets Manager. It never appears in source, and
  `.env` is git-ignored while `.env.example` holds only the variable name.
- No `VITE_*` variable ever carries a provider key; anything with that prefix is compiled into the
  browser bundle by design.
- `make secret-scan` scans every tracked file for key-shaped strings, and CI runs both it and
  gitleaks on every pull request.
- The API-key value is never logged. Provider calls log model, latency, status, and usage metadata
  only, never the `Authorization` header and never the raw request.
- Error responses are Problem Details generated from a fixed `ErrorCode` table. Provider response
  bodies are never forwarded to the client.
- `GET /api/v1/config` has a test asserting that no provider or infrastructure identifier appears in
  its response.

**Residual risk:** anyone with shell access to the container can read the environment. Rotation
steps are in `docs/aws-runbook.md`.

---

## T2 — Cost abuse

**Vectors:** a stranger finds the API and generates exercises; a bug retries generation in a loop;
a user reloads a page that regenerates on mount.

**Controls**

- Cognito self-registration is disabled. Accounts are created by the operator only.
- Every `/api/v1` route requires a valid token.
- Bucket4j per-user burst limiting on the expensive create/evaluate endpoints, plus configurable
  daily caps (`APP_LIMITS_LISTENING_PER_DAY`, `APP_LIMITS_SPEAKING_PER_DAY`) counted in DynamoDB.
- Generation retries are bounded: at most one revalidation retry, then a stable `GENERATION_INVALID`
  error. There is no unbounded retry loop anywhere in the provider adapters.
- `Idempotency-Key` on cost-incurring POSTs so a double-submit does not double-charge.
- An AWS Budgets alert on the account.

**Known limitation:** the rate limiter is in-process. With one Fargate task that is exact; if the
service is ever scaled to two tasks, each gets its own bucket and the effective limit doubles. The
daily caps are stored in DynamoDB and stay correct regardless of task count. Moving the burst
limiter to DynamoDB or ElastiCache is the fix when it is needed.

---

## T3 — Answer and transcript leakage

**Vectors:** the pre-submission response includes the answer key; the client hides it with CSS; a
debug endpoint or an OpenAPI example exposes it; the exercise is re-fetched with a different
parameter.

**Controls**

- The pre-submission response is a **separate type** with no `speakerTurns`, `correctOptionId`,
  `explanation`, or `evidence` field. There is nothing to omit at serialization time because the
  fields do not exist on that type.
- A test asserts the serialized pre-submission JSON contains none of those keys.
- Scoring happens on the server from the persisted exercise. The client's submission carries only
  the selected option ids.
- The "play once" toggle is documented as a learning constraint, not a security control. A
  determined user can replay audio; that is their choice and it costs them nothing but practice
  value.

---

## T4 — Cross-user access

**Vectors:** guessing another user's exercise id; a path parameter used directly as a key; a
presigned URL shared or leaked.

**Controls**

- Every read is keyed by `USER#{cognitoSub}` from the verified token. The user id is never taken
  from the path or body, so a cross-user read is not expressible in the repository API.
- A miss returns `404`, never `403`, so probing cannot confirm existence.
- Presigned GET URLs are short-lived (default 15 minutes) and issued only to the authenticated
  owner of the object.
- Integration tests attempt cross-user access on every owned resource and assert `404`.

---

## T5 — Malicious upload

**Vectors:** a huge file to exhaust disk or memory; a disguised executable; a filename used in a
shell command; a crafted media file aimed at the decoder.

**Controls**

- Content type must be in an allowlist (`audio/webm`, `audio/ogg`, `audio/mp4`, `audio/mpeg`,
  `audio/wav`); size is capped by both the servlet container and an explicit application check;
  duration is capped by the task's own limit.
- Temporary files use application-generated UUID paths under a controlled directory. A
  client-supplied filename is never used as a path.
- FFmpeg is invoked with an argument array, never a shell string, and generated text is never
  interpolated into a command.
- Temporary files are deleted in `finally` blocks, and an S3 lifecycle rule expires anything missed
  after one day.
- Recordings are deleted after evaluation unless `APP_SPEAKING_RETAIN_RECORDINGS=true`.

---

## T6 — Log leakage

**Vectors:** a full request body logged at DEBUG; a stack trace returned to the client; a transcript
in an error message; an `Authorization` header in an access log.

**Controls**

- `server.error.include-message=never` and `include-stacktrace=never`.
- Logs carry request/correlation id, latency, model, status, and token usage. Never audio bytes,
  never a full transcript, never a header value.
- Unexpected exceptions are logged server-side with their stack trace and returned to the client as
  a generic `INTERNAL_ERROR`.
- CloudWatch retention is short and cost-conscious.

---

## T7 — Prompt injection through generated or user content

A generated exercise or a user's spoken answer is **data**, not instructions. Generated content is
validated against a strict schema and rejected if it fails; it is never executed, never used to
build a command, and never used to construct a file path. Scoring prompts instruct the model to
ground every criticism in supplied evidence and to judge language quality rather than agreement with
the speaker's opinion.

---

## Transport and browser controls

- HTTPS everywhere; HSTS with `includeSubDomains`; `X-Content-Type-Options: nosniff`;
  `X-Frame-Options: DENY`.
- CORS is an explicit origin allowlist with `allowCredentials=false`. No wildcard, ever.
- **CSRF protection is disabled deliberately.** The API is stateless and reads credentials only from
  the `Authorization` header, never from an ambient cookie, so no cross-site request can carry the
  caller's identity. **If the token is ever moved into a cookie, CSRF protection must be reinstated
  in the same change.**
- The S3 buckets block all public access; CloudFront reaches the static bucket through Origin Access
  Control only.

## IAM

The Fargate task role is scoped to exactly one DynamoDB table, one S3 bucket, and one secret, with
enumerated actions. No `Action: "*"` and no `Resource: "*"` is granted anywhere without a written
justification in this file. There is currently none.

## Reporting

This is a personal project with a single user. Rotate the OpenAI key
(`docs/aws-runbook.md`) at the first sign of exposure — rotation is cheap, investigation is not.
