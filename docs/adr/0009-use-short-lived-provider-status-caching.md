# ADR 0009: Use Short-Lived Provider Status Caching

- Status: Accepted

## Context

Provider status checks may involve live discovery behavior, especially for model availability and external provider readiness. Re-running those checks aggressively can make the UI noisy and the backend unnecessarily chatty.

## Decision

Use short-lived provider status caching and expose `Last checked` plus a manual `Refresh status` action in the UI.

## Rationale

- status is a lightweight troubleshooting surface, not the authoritative execution path
- short-lived caching reduces repetitive live checks while keeping the UI informative
- a manual refresh action gives the user control without forcing constant background churn

## Consequences

Positive:

- steadier UI status behavior
- reduced provider discovery noise
- clearer troubleshooting through explicit timestamps

Negative:

- status can be briefly stale
- users must understand the distinction between cached status and real request-time failures

## Revisit Triggers

Reevaluate this decision if:

- status freshness becomes more important than the current local-noise tradeoff
- the project later adds more advanced provider health/diagnostic features
