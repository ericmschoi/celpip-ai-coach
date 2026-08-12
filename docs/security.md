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
  caller's identity. Tokens are held in `sessionStorage`, which is never attached to a request
  automatically. **If the token is ever moved into a cookie, CSRF protection must be reinstated in
  the same change.**
- Sign-in uses Authorization Code with PKCE against the Cognito hosted UI. The browser client has no
  secret, and an intercepted authorization code cannot be redeemed without the verifier.
- The S3 buckets block all public access; CloudFront reaches the static bucket through Origin Access
  Control only.

## IAM

The Fargate task role is scoped to exactly one DynamoDB table, one S3 bucket, and one secret, with
enumerated actions. No `Action: "*"` is granted anywhere in the stack, and a CDK assertion test
fails the build if one appears.

**The single justified `Resource: "*"`:** `ecr:GetAuthorizationToken` on the ECS task *execution*
role. That call requests an account-scoped registry token and does not support resource-level
permissions, so AWS requires `"*"`. It grants no access to any image by itself — pulling still
requires `ecr:BatchGetImage` on the specific repository, which is scoped.

One wildcard was removed rather than justified: CDK's `BucketDeployment` grants its lambda
`cloudfront:CreateInvalidation` on `"*"` when you wire a distribution to it. The stack therefore
does not wire one, and the deploy workflow invalidates `/index.html` with the AWS CLI instead.

## Deployed network posture

The load balancer accepts HTTP on port 80 from anywhere, and the Fargate task's security group
accepts traffic only from the load balancer's security group. The task is not reachable directly.

Browsers never talk to the load balancer: `/api/*` is routed through CloudFront, so the browser is
always on HTTPS and the app is same-origin, which is also why no CORS preflight happens in
production.

**Accepted trade-off:** the load balancer origin is reachable over plain HTTP by anyone who
discovers its DNS name. Every route behind it still requires a valid Cognito access token, so this
exposes no data — but a client that deliberately called the origin directly would send its bearer
token unencrypted. Nothing in this app does that. The hardening step, if this ever mattered, is to
restrict the load balancer's security group to the AWS-managed CloudFront origin-facing prefix list,
or to put an ACM certificate on the load balancer. Both are noted in `docs/aws-runbook.md`.

## Known dependency advisory

`npm audit` reports one high-severity finding in `infra/`, and it is accepted rather than fixed:

| Field | Value |
| --- | --- |
| Package | `brace-expansion` 5.0.8 |
| Advisory | GHSA-rgw5-rvv9-x895 — denial of service via unbounded intermediate arrays |
| Path | `aws-cdk-lib` → bundled `minimatch` → `brace-expansion` |

**Why it is not fixed here.** `aws-cdk-lib` ships `minimatch` as a *bundled* dependency, and npm
`overrides` cannot rewrite a bundled package's own dependencies — adding one changes nothing, which
was verified rather than assumed. The fix has to come from an `aws-cdk-lib` release that bundles a
patched `minimatch`; 2.264.0 is the newest available.

**Why the exposure is nil in practice.** This is a build-time dependency of the infrastructure
project only. It is not in the backend, not in the frontend bundle, and not in the container image.
The only glob patterns it ever expands are the ones written in this repository's own CDK code, and
it never sees user input or network data. Re-check after each `aws-cdk-lib` upgrade.

The frontend has no outstanding advisories at any severity.

## Reporting

This is a personal project with a single user. Rotate the OpenAI key
(`docs/aws-runbook.md`) at the first sign of exposure — rotation is cheap, investigation is not.
