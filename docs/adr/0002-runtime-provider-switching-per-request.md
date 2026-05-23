# ADR 0002: Use Runtime Provider Switching Per Request

- Status: Accepted

## Context

`Local GenAI Lab` supports multiple LLM providers:

- Ollama
- Amazon Bedrock
- Hugging Face

The project started from a simpler model where one provider could be treated as a backend startup choice. That model became limiting once the UI needed to compare providers in the same running application and the same saved session.

Two broad options existed:

1. keep provider choice as a backend startup concern
2. resolve the provider per request at runtime

## Decision

Use runtime provider switching per request.

The backend accepts provider selection on chat and model-discovery flows and resolves the selected provider through the `ChatModelProviderRegistry`.

## Rationale

- the project is a local-first GenAI lab where comparing providers is a core use case
- provider switching in the same running backend process improves experimentation and usability
- per-request selection fits the current frontend provider/model selector design
- mixed-provider sessions become possible without splitting the application into multiple backend modes

## Consequences

Positive:

- the UI can switch providers without restarting the backend
- sessions can contain assistant turns from different providers
- provider-aware `/api/models` and provider status flows make the system more inspectable

Negative:

- provider selection logic becomes part of request handling instead of simple startup wiring
- backend APIs and UI state must carry provider information explicitly
- provider-specific configuration still needs to exist in the running backend process

## Revisit Triggers

Reevaluate this decision if:

- the application moves away from provider comparison as a primary use case
- per-request provider switching becomes operationally expensive or confusing
- the project splits into separate deployment modes where one provider per process becomes simpler again
