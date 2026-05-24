# ADR 0008: Use Curated Hugging Face Candidates Instead Of Full Catalog Browsing

- Status: Accepted

## Context

Hugging Face exposes a very large model ecosystem, but not every model is appropriate or usable through the hosted chat path this project uses.

Possible approaches included:

1. expose the broad Hugging Face catalog directly in the UI
2. use a curated configured candidate list and validate the usable subset at runtime

## Decision

Use a curated configured candidate list for Hugging Face and validate the usable subset at runtime.

## Rationale

- not every Hugging Face model is suitable for the same hosted chat API path
- a giant raw catalog would create noise and misleading choices
- a curated candidate list is simpler, more stable, and more aligned with the project’s local-lab goals

## Consequences

Positive:

- cleaner UI model selection
- better control over what models are expected to work
- clearer troubleshooting when candidates are rejected

Negative:

- the Hugging Face selector is intentionally narrower than the full public catalog
- adding models requires configuration rather than pure discovery

## Primary Implementation

- [HuggingFaceClient.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/client/HuggingFaceClient.java)
- [AvailableModelsService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/service/AvailableModelsService.java)
- [ProviderStatusService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/service/ProviderStatusService.java)
- [Home.jsx](/Users/jrodolfo/workspace/aws/local-genai-lab/frontend/src/pages/Home.jsx)

## Revisit Triggers

Reevaluate this decision if:

- the project later needs richer Hugging Face browsing or search
- the hosted provider path becomes broad and reliable enough that direct catalog integration makes sense
