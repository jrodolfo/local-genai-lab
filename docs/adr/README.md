# Architecture Decision Records

This folder stores Architecture Decision Records (ADRs) for `Local GenAI Lab`.

ADRs capture the architectural decisions that shaped the repository, why those
decisions were made, and what tradeoffs they introduced. They complement
[`docs/architecture.md`](../architecture.md), which describes the current
system design, by preserving the decision history behind that design.

## Why This Repo Uses ADRs

`Local GenAI Lab` has several decisions that are easier to maintain when the
reasoning is written down explicitly, for example:

- why the MCP server is a separate TypeScript runtime
- why provider selection happens per request
- why the UI only shows configured providers
- why Hugging Face uses curated candidate validation instead of full catalog browsing
- why Mermaid is the maintained architecture source of truth

These records are intentionally short enough to scan quickly during onboarding,
maintenance, or interview preparation.

## Status Values

- `proposed`: decision is being discussed and is not yet the project baseline
- `accepted`: decision is in effect and reflected in the codebase
- `superseded`: decision was replaced by a newer ADR

## When To Add A New ADR

Add an ADR when a change introduces or revises a durable architectural decision,
for example:

- runtime boundaries
- storage model choices
- provider-selection model
- protocol contract choices
- security boundaries
- major documentation-source-of-truth decisions

Do not add an ADR for ordinary implementation work such as:

- UI wording polish
- styling changes
- routine bug fixes
- test additions without a new design decision

## Naming Convention

Use zero-padded numeric prefixes and kebab-case titles:

```text
0001-short-kebab-case-title.md
```

Each ADR should focus on one decision.

## Template

Start new records from [template.md](template.md).

## Current ADRs

- [0001-mcp-separate-typescript-runtime.md](0001-mcp-separate-typescript-runtime.md): keep MCP tool execution in a separate TypeScript runtime.
- [0002-runtime-provider-switching-per-request.md](0002-runtime-provider-switching-per-request.md): allow each chat request to select its provider and model.
- [0003-show-only-configured-providers-in-selector.md](0003-show-only-configured-providers-in-selector.md): show only backend-configured providers in the UI.
- [0004-keep-tool-execution-behind-mcp-boundary.md](0004-keep-tool-execution-behind-mcp-boundary.md): keep shell-tool execution behind the MCP boundary.
- [0005-ground-tool-results-through-the-selected-model.md](0005-ground-tool-results-through-the-selected-model.md): send successful tool results back through the selected model.
- [0006-persist-sessions-as-local-json-files.md](0006-persist-sessions-as-local-json-files.md): store sessions as local JSON files.
- [0007-restrict-artifact-access-to-configured-reports-root.md](0007-restrict-artifact-access-to-configured-reports-root.md): keep artifact access read-only and bounded to the reports root.
- [0008-use-curated-hugging-face-candidates-not-full-catalog.md](0008-use-curated-hugging-face-candidates-not-full-catalog.md): validate curated Hugging Face model candidates instead of browsing the full catalog.
- [0009-use-short-lived-provider-status-caching.md](0009-use-short-lived-provider-status-caching.md): cache provider status briefly and expose manual refresh.
- [0010-use-unified-backend-startup-script.md](0010-use-unified-backend-startup-script.md): use one backend startup helper for provider configuration.
- [0011-use-mermaid-as-architecture-source-of-truth.md](0011-use-mermaid-as-architecture-source-of-truth.md): keep Mermaid diagrams as the maintained architecture diagram source.
- [0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md](0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md): add an isolated RAG workspace over the local docs corpus using in-memory lexical retrieval.
