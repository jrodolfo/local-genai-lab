# ADR 0003: Show Only Configured Providers In The Selector

- Status: `accepted`
- Date: `unknown`

## Context

The codebase can support multiple providers, but not every provider is always configured in the currently running backend process.

Without an availability rule, the UI risks showing providers that:

- exist in the codebase
- but cannot actually serve requests in the current runtime

This creates misleading UX, especially when one provider is selected as the startup default but other providers are not configured.

## Decision

Show only providers configured in the running backend process in the UI selector.

The backend remains the source of truth for which providers are available to the frontend at runtime.

## Rationale

- a provider that cannot actually run should not appear as a normal selectable option
- the running backend process, not the codebase alone, determines real availability
- this keeps runtime provider switching honest instead of merely aspirational

## Consequences

Positive:

- the UI avoids exposing provider states that are guaranteed to fail
- provider availability is clearer for local use
- documentation and `.env` workflow align more closely with actual runtime behavior

Negative:

- “supported by the repo” and “available right now” are intentionally different concepts
- users must configure multiple providers in the same process if they want to switch among them live

## Primary Implementation

- [AvailableModelsService.java](../../backend/src/main/java/net/jrodolfo/llm/service/AvailableModelsService.java)
- [ProviderStatusService.java](../../backend/src/main/java/net/jrodolfo/llm/service/ProviderStatusService.java)
- [Home.jsx](../../frontend/src/pages/Home.jsx)

## Revisit Triggers

Reevaluate this decision if:

- the UI later adds a clear disabled-provider model with strong inline explanations
- the project adopts a richer provider management surface than the current local selector
