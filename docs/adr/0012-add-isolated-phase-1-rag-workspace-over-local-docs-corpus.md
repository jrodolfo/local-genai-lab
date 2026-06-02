# Title

Add isolated phase-1 RAG workspace over local docs corpus

# Status

Accepted

# Date

2026-05-27

# Context

`Local GenAI Lab` already included:

- multi-provider chat
- MCP-backed tool execution
- local JSON session persistence
- artifact inspection

The repository did not yet have a retrieval-augmented generation capability for
local knowledge sources such as project documentation and ADRs.

Adding RAG directly into the existing `/api/chat` and tool-orchestration flow
would have mixed several concerns too early:

- normal chat orchestration
- MCP/tool routing
- retrieval routing
- session behavior changes
- future corpus-management questions such as uploads or vector-backed retrieval

The first RAG slice needed to be useful, teachable, and low-risk without
claiming capabilities the project did not yet have, such as uploads, embeddings,
or an external vector database.

# Decision

Add an isolated phase-1 RAG feature with these boundaries:

- expose RAG through a separate frontend `RAG` workspace
- expose RAG through separate backend `/api/rag/*` endpoints
- use a fixed local corpus rooted at `docs/`
- chunk the local documents and retrieve through the `RagRetrievalStore` abstraction
- use `InMemoryLexicalRagRetrievalStore` as the phase-1 retrieval implementation
- keep lexical retrieval available as the baseline implementation for future comparison
- return provider-generated answers with cited source chunks
- keep RAG separate from `/api/chat`, MCP tooling, and session persistence

Do not add in phase 1:

- document uploads
- report/artifact corpus ingestion
- embeddings
- external vector storage such as Qdrant
- a retrieval-mode selector before a second implementation exists
- automatic routing between normal chat, tools, and RAG

# Rationale

This design keeps the first RAG slice:

- small enough to evaluate honestly
- architecturally separate from the existing chat/tool system
- easy to extend later with richer retrieval infrastructure
- useful for repo-documentation grounding and provider comparison

The chosen retrieval path is intentionally modest. The important phase-1 goal is
not to claim production-grade vector search. It is to establish a docs-grounded
RAG workflow with visible citations while keeping lexical retrieval as a
zero-dependency baseline.

# Primary Implementation

- [RagController.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/controller/RagController.java)
- [RagAnswerService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/service/RagAnswerService.java)
- [RagCorpusService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/service/RagCorpusService.java)
- [RagRetrievalStore.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/store/RagRetrievalStore.java)
- [InMemoryLexicalRagRetrievalStore.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/rag/store/InMemoryLexicalRagRetrievalStore.java)
- [RagWorkspace.jsx](/Users/jrodolfo/workspace/aws/local-genai-lab/frontend/src/pages/RagWorkspace.jsx)

# Consequences

Positive:

- the repository now has a clear experimental RAG capability
- local docs and ADRs can be queried through a dedicated UI
- provider behavior can be compared in a retrieval-grounded setting
- the retrieval abstraction can support both lexical and vector-backed implementations later
- future vector-backed retrieval can be added as a second mode instead of replacing lexical retrieval

Negative:

- RAG is a separate experience instead of a unified chat mode
- phase 1 retrieval quality is limited by the current in-memory lexical approach
- the corpus is intentionally narrow

Neutral:

- future expansion can add embeddings, vector-backed retrieval, or broader corpus
  support without rewriting the current chat/tool orchestration path
- a future UI selector should be added only after multiple retrieval implementations exist

# Revisit Triggers

Revisit this decision if:

- retrieval quality remains weak after prompt and chunking improvements
- the corpus needs to expand beyond `docs/`
- the project adds upload-based document workflows
- the product needs unified routing between normal chat, tools, and RAG
- vector-backed retrieval becomes justified by scale or evaluation results
