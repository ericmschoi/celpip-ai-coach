# CLAUDE.md

Working notes for anyone (human or agent) making changes here.

## What this is

ListenSpeak AI Coach — an independent, unofficial CELPIP-style Listening and Speaking practice app.
Monorepo: `frontend/` (React + TS + Vite), `backend/` (Java 21 + Spring Boot 4), `infra/` (AWS CDK).

## Commands

Run everything from the repository root via `make` (see `make help`). Java 21 is required;
the Makefile resolves `JAVA_HOME` to `openjdk@21` automatically.

```bash
make test        # backend + frontend + infra
make lint
make build
make e2e         # SEED mode, no provider calls
make infra-synth
make secret-scan
```

## Non-negotiables

1. **No official CELPIP content.** No real questions, recordings, screenshots, logos, or study
   material. Everything is generated originally. The independence disclaimer must stay visible.
2. **Secrets never enter the repository**, the frontend bundle, logs, fixtures, or docs. The OpenAI
   key comes from the backend environment or Secrets Manager only.
3. **The frontend never calls OpenAI.** All provider traffic goes through the Spring backend.
4. **Answers and transcripts are absent from the pre-submission type**, not hidden by CSS or client
   state. If you add a field to the listening read model, check which type it belongs on.
5. **Ownership is keyed by the token subject**, never by a path parameter. Cross-user misses return
   `404`.
6. **SEED mode must keep working.** A fresh clone with no API key must still run the full app and
   the whole e2e suite.
7. **No unbounded retries** against a paid provider. One revalidation retry, then a stable error.
8. **Task timings and model names are configuration**, not literals scattered through components or
   adapters (`SpeakingTaskCatalog`, `AppProperties`).

## Conventions

- Backend is packaged by feature (`listening/`, `speaking/`, `config/`) with `platform/` for
  cross-cutting concerns. Controller → application service → domain → provider adapter.
- Every API failure is RFC 9457 Problem Details with a stable `code` from `ErrorCode`.
- Frontend validates every response with Zod at the `apiClient` boundary. Components never see
  unvalidated server data.
- Strict TypeScript. No `any`.
- Tests cover the expensive and security-sensitive seams, not coverage percentage.

## Before committing

`make lint && make test && make secret-scan`.
