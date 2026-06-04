# Title

Add request-scoped RAG retrieval selection and comparison

# Status

Accepted

# Date

2026-06-04

# Context

ADR 0012 added an isolated RAG workspace over the local `docs/` corpus using
in-memory lexical retrieval. ADR 0013 established the phase-2 direction for
Ollama embeddings and Qdrant-backed vector retrieval while keeping lexical
retrieval available.

After vector and Qdrant retrieval became available, the old startup-config-only
workflow became too awkward for a lab project:

- developers had to restart the app to compare retrieval modes cleanly
- Qdrant readiness needed to be visible before a user selected it
- saved RAG answers needed to show which retrieval path produced them
- lexical, in-memory vector, and Qdrant results needed to be compared against
  the same prompt without manual repetition

The project needed this comparison workflow without merging RAG into normal
chat, MCP tool execution, uploads, or automatic agent routing.

# Decision

Make RAG retrieval target selection request-scoped inside the isolated RAG
workflow.

The backend and UI now support:

- backend-provided retrieval targets through `/api/rag/status`
- selectable targets for `Lexical`, `Vector - In Memory`, and `Vector - Qdrant`
- readiness messages and availability flags for each retrieval target
- disabled Qdrant selection when Qdrant is unavailable or the collection is not
  ready
- `Rebuild Index` applying to the currently selected retrieval target
- RAG query requests carrying retrieval mode and vector store values
- saved RAG answers persisting retrieval metadata
- session reopen, JSON export/import, and Markdown export preserving retrieval
  metadata
- `Compare Retrieval Targets` running one prompt against available retrieval
  targets without saving those comparison results as normal session turns

Backend environment variables such as `RAG_RETRIEVAL_MODE` and
`RAG_VECTOR_STORE` remain useful as startup defaults and operational settings.
They are no longer the normal way to compare retrieval strategies in the UI.

# Rationale

This keeps the lab workflow honest and practical.

Request-scoped selection lets a developer start the app once with
`./restart.sh`, then compare retrieval behavior directly in the UI. That is a
better fit for a local experimentation project than repeatedly restarting the
backend for each mode.

Backend-provided readiness keeps the UI from guessing whether Qdrant is usable.
The backend owns the runtime checks for configured retrieval targets, while the
frontend renders the selector and target-specific messages.

Persisting retrieval metadata makes saved RAG answers auditable. When a session
is reopened or exported, it is clear whether the answer came from lexical,
in-memory vector, or Qdrant vector retrieval.

Keeping comparison runs non-persistent avoids polluting normal RAG conversation
history with evaluation-only output. A developer can compare targets first, then
use `Ask Docs Corpus` to save the selected answer that should become part of the
session.

The decision also protects the boundaries from ADR 0012:

- RAG still uses separate `/api/rag/*` endpoints
- RAG remains separate from `/api/chat`
- RAG does not invoke MCP tools
- RAG does not add uploads or report-corpus ingestion
- RAG does not add automatic routing between chat, tools, and retrieval

# Primary Implementation

- [RagController.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/controller/RagController.java)
- [RagStatusResponse.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/dto/RagStatusResponse.java)
- [RagRetrievalTargetResponse.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/dto/RagRetrievalTargetResponse.java)
- [RagQueryRequest.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/dto/RagQueryRequest.java)
- [RagRetrievalMetadata.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/dto/RagRetrievalMetadata.java)
- [RagAnswerService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/service/RagAnswerService.java)
- [RagCorpusService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/service/RagCorpusService.java)
- [RagWorkspace.jsx](/Users/jrodolfo/workspace/aws/local-genai-lab/frontend/src/pages/RagWorkspace.jsx)
- [RagAnswerWithSources.jsx](/Users/jrodolfo/workspace/aws/local-genai-lab/frontend/src/components/RagAnswerWithSources.jsx)

# Consequences

Positive:

- users can compare retrieval targets without restarting the app
- Qdrant readiness is visible before selection
- unavailable Qdrant targets are disabled instead of failing as generic backend
  errors
- saved RAG answers carry retrieval provenance
- JSON and Markdown exports preserve retrieval provenance
- comparison results stay separate from normal saved RAG conversations
- lexical retrieval remains visible as a baseline instead of being hidden by
  vector retrieval

Negative:

- `/api/rag/status` now has a richer frontend contract
- RAG query requests carry more retrieval-specific fields
- the UI has more state to manage around selected target, rebuild target, and
  comparison output
- comparison output is transient and not yet exportable as a dedicated
  evaluation report

Neutral:

- startup defaults still exist for scripting and status checks
- Qdrant remains optional for default local startup
- provider/model selection remains independent from retrieval target selection

# Revisit Triggers

Revisit this decision if:

- comparison results need to become exportable evaluation reports
- the project adds upload-based or multi-corpus RAG workflows
- retrieval target readiness needs to include per-index embedding model
  compatibility
- the UI needs to compare multiple answer providers and retrieval targets in one
  matrix
- automatic routing between chat, tools, and RAG becomes a project goal
