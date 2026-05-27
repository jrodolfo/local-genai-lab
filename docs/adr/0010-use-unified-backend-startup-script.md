# ADR 0010: Use A Unified Backend Startup Script

- Status: Accepted

## Context

The project originally had provider-specific backend startup scripts. As the system evolved toward multiple configured providers in one running process, those scripts became more confusing than helpful.

## Decision

Use one unified backend startup script:

- `ops/start-backend-helper.sh`

That script auto-loads repo-local `.env` when present and starts the backend with the configured provider set, while `APP_MODEL_PROVIDER` selects the default provider.

## Rationale

- multiple provider-specific scripts were legacy ergonomics from an earlier architecture phase
- one script better matches the current runtime-provider-switching model
- a unified startup path reduces docs drift and operational complexity

## Consequences

Positive:

- simpler onboarding
- clearer `.env`-driven workflow
- better alignment between runtime switching and startup behavior

Negative:

- provider-specific startup flows are less explicit than separate wrapper names
- users need to understand `.env` and `APP_MODEL_PROVIDER` more directly

## Revisit Triggers

Reevaluate this decision if:

- the project later requires clearly distinct deployment modes again
- a single startup path becomes ambiguous for future non-local environments
