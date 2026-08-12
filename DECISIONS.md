# Architecture decision record

Short entries. Newest last. Each records the decision actually made, why, and what it costs.

---

## ADR-001 — Spring Boot 4.1.0 on Java 21

**Status:** accepted (phase 1)

The prompt requires Java 21 and a current stable Spring Boot. Spring Boot 4.1.0 is the current GA
release and supports Java 21. The dependency set was verified against Maven Central before locking:
springdoc-openapi 3.1.0 (the Boot 4 line), AWS SDK v2 BOM 2.46.7, openai-java 4.50.0,
Bucket4j 8.19.0.

**Cost:** Spring Boot 4 defaults to Jackson 3 (`tools.jackson.*`) and Spring Security 7. Any
snippet written for Boot 3 needs adjusting. In exchange the project starts on a version with a long
support runway instead of one already mid-life.

**Note:** the host machine had JDK 25, 17, 11, and 8 but no 21, so `openjdk@21` was installed via
Homebrew. The `Makefile` resolves `JAVA_HOME` to it automatically and can be overridden.

---

## ADR-002 — Single DynamoDB table, no relational database

**Status:** accepted (phase 1)

Access patterns are all "everything for one user, by type, newest first":

| Pattern                              | Key                                                |
| ------------------------------------ | -------------------------------------------------- |
| Fetch one exercise for its owner     | `PK=USER#{sub}`, `SK=EXERCISE#{exerciseId}`        |
| List a user's listening attempts     | `PK=USER#{sub}`, `SK begins_with LISTENING_ATTEMPT#`|
| List a user's speaking evaluations   | `PK=USER#{sub}`, `SK begins_with SPEAKING_EVALUATION#`|
| Enforce a daily cap                  | `PK=USER#{sub}`, `SK=USAGE#{yyyy-mm-dd}`           |

Every one of these is a single-partition query, so a single on-demand table with TTL covers the app
with no idle cost and no schema migrations. A relational database would add a always-on instance
charge for no access pattern that needs it.

**Cost:** cross-user analytics or ad-hoc reporting would be awkward. Neither is in scope.

---

## ADR-003 — Ownership is enforced by the partition key, and answers never enter the pre-submission DTO

**Status:** accepted (phase 1)

Two rules that the rest of the design depends on:

1. Every read is keyed by `USER#{cognitoSub}` taken from the verified token, never from a path
   parameter. A cross-user read cannot be expressed, so it cannot leak. A miss returns `404`, not
   `403`, so probing cannot confirm that another user's resource exists.
2. The pre-submission response type is a *separate type* that has no `speakerTurns`,
   `correctOptionId`, `explanation`, or `evidence` fields at all. Secrecy is a property of the type,
   not of a serializer setting, a CSS rule, or client state.

---

## ADR-004 — Stateless bearer-token API, CSRF protection disabled deliberately

**Status:** accepted (phase 1)

The API is stateless and authenticates only from the `Authorization` header. It never reads an
ambient cookie, so a cross-site request cannot carry the caller's identity and CSRF tokens would
protect nothing. CORS is a strict origin allowlist with `allowCredentials=false`.

**Cost:** if a future change ever stores the token in a cookie, CSRF protection must be reinstated
in the same commit. This is called out in `docs/security.md`.

---

## ADR-005 — SEED mode as a first-class runtime mode

**Status:** accepted (phase 1)

`APP_CONTENT_MODE=SEED` serves deterministic fixture content through the same controllers, services,
and DTOs as live generation. It exists so local development, CI, and the whole Playwright suite run
without spending provider credits, and so a provider outage still leaves a demonstrable app.

**Cost:** two code paths behind each provider port must stay in sync. They share the same domain
model and validation, which is what makes that manageable.

---

## ADR-006 — oxlint instead of ESLint

**Status:** accepted (phase 1)

The Vite 9 React-TS template ships oxlint, and `typescript-eslint` 8.67 declares a peer range of
`typescript >=4.8.4 <6.1.0`, which excludes the current TypeScript 7. Rather than pin an old
TypeScript to satisfy a linter, the project uses oxlint plus `tsc --noEmit` for type errors and
Prettier for formatting. TypeScript is pinned to the 6.0.x line.

---

## ADR-007 — One CDK stack, constructs for separation

**Status:** accepted (phase 1)

A personal app deploys as a unit. Splitting into several stacks would add cross-stack exports that
lock resources and complicate `cdk destroy`, with no independent deployment cadence to justify it.
Separation of concerns lives in `infra/lib/constructs/`.

---

## ADR-008 — The seed fixture's audio is rendered offline, not by the paid TTS

**Status:** accepted (phase 2)

The Part 5 demo exercise needs audio that works with no API key, in CI, and in a fresh clone. The
committed MP3 is rendered by `scripts/generate-seed-audio.sh` from the fixture's own JSON using
offline macOS `say` voices (Samantha, Daniel, Karen — different accents as well as pitches), then run
through exactly the same loudness normalisation, limiting, and quality gate as generated audio.

**Cost:** the fixture voices sound more synthetic than `marin`/`cedar`. That is acceptable, and
documented in the UI as demo mode: the fixture exists so the flow works for free, not to demonstrate
audio quality. The script must be re-run when the transcript changes, and `audioDurationSeconds`
updated in the JSON.

---

## ADR-009 — Local audio is served through a signed, expiring link, not an authenticated endpoint

**Status:** accepted (phase 2)

An `<audio>` element cannot send an `Authorization` header, so audio cannot go through the normal
bearer-token API. In AWS the answer is an S3 presigned GET. Locally, `LocalAudioStorage` issues an
equivalent: `/media/listening?token=…` where the token is `base64(key):expiry:HMAC-SHA256`, signed
with a per-process random key.

Per-process means every restart invalidates old links, which is correct for a development mechanism.
The route is permitted without authentication because the signature *is* the credential, and the
controller exists only when `app.storage.mode=LOCAL`, so a deployed environment has no such route at
all. Expired and forged tokens are both reported as `404`, so nothing is learned from probing.

---

## ADR-010 — Provider adapters are tested at the port, not over HTTP

**Status:** accepted (phase 2)

The prompt suggests WireMock or MockWebServer. Instead, the seams that can actually break are tested
directly: `ExerciseValidator` against 13 malformed exercises, `AudioAssembler` and `AudioQualityGate`
against **real FFmpeg** output, `DialogueRenderer` against a fake `TextToSpeech`, and provider
failure mapping through the real controller with a mocked generator.

Reasoning: an HTTP-level mock would assert that this app sends the JSON it was written to send, which
is not a property worth a test. Whether generated audio clips, whether turns survive concurrency in
order, and whether an upstream 429 becomes a stable `PROVIDER_RATE_LIMITED` body — those are.

**Cost:** the exact request shape sent to OpenAI is not covered by a test. It is covered by the
typed SDK and by the documentation checks recorded in `docs/prompts.md`.
