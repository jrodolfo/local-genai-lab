# ADR 0013: Use Ollama Embeddings And Qdrant For Phase-2 RAG Vector Retrieval

- Status: `accepted`
- Date: `2026-06-02`

## Context

`Local GenAI Lab` already has an isolated phase-1 RAG workspace over the local
`docs/` corpus. That first slice intentionally uses in-memory lexical retrieval
through `InMemoryLexicalRagRetrievalStore`.

Lexical retrieval is useful as a zero-dependency baseline, but it has a known
limitation: it works best when the user's question uses similar terms to the
source documents. Questions with different wording but similar meaning can miss
relevant chunks.

The project needs a phase-2 direction for vector retrieval that improves
semantic matching without turning the lab into a larger document-management
system too early.

Important constraints:

- keep RAG isolated from the main `/api/chat` and MCP tool flows
- keep the fixed local `docs/` corpus for the next implementation slice
- do not add uploads yet
- do not remove lexical retrieval
- keep the implementation local-first where practical
- keep the answer provider independent from the embedding runtime

## Decision

For phase-2 RAG vector retrieval, use this direction:

- keep lexical retrieval available as the baseline and fallback mode
- add vector retrieval as a second `RagRetrievalStore` implementation
- use Ollama as the first embedding runtime
- use `nomic-embed-text` as the first embedding model
- use Qdrant as the first external vector database candidate
- store chunk vectors with citation metadata such as source path, chunk id,
  excerpt, corpus root, and embedding model
- use the same embedding model at index time and query time
- keep answer generation routed through the selected RAG provider/model
- expose vector retrieval through RAG-specific configuration and status fields
- add a UI retrieval-mode selector only after lexical and vector retrieval both
  work end to end

This decision accepts the architectural direction. It does not mean vector
retrieval is already implemented.

## Rationale

Ollama embeddings are the best first candidate for this repository because they
fit the local-first workflow and do not require cloud credentials. The current
developer environment already has `nomic-embed-text` installed, which makes it
a practical baseline for experimentation.

Qdrant is the best first external vector database candidate because it is
well-suited to small local RAG experiments, runs cleanly with Docker, and keeps
vector storage outside the Spring Boot process without introducing a heavier
search stack.

Keeping the embedding runtime separate from the answer provider preserves the
existing provider-comparison value of the lab. The system can retrieve chunks
with Ollama embeddings and still generate the final answer with Ollama,
Bedrock, or Hugging Face.

Keeping lexical retrieval available is intentional. It gives the project a
dependency-free fallback and a clear baseline for deciding whether vector
retrieval improves real prompts.

## Supporting Design Note

The detailed phase-2 proposal lives in:

- [rag-phase-2-vector-retrieval-design.md](../rag-phase-2-vector-retrieval-design.md)

## Consequences

Positive:

- the project has a clear vector retrieval direction before implementation
- lexical and vector retrieval can be compared directly
- local embeddings avoid cloud credential friction for the first vector slice
- answer generation remains provider-selectable
- Qdrant gives the lab a realistic vector database path without overcommitting
  to a managed production search platform

Negative:

- vector retrieval adds an operational dependency once Qdrant is implemented
- embedding model changes require reindexing
- vector retrieval can still return semantically plausible but source-weak
  chunks
- the implementation will need new test coverage around embeddings, indexing,
  retrieval mode selection, and status reporting

Neutral:

- uploads, report artifact ingestion, MCP retrieval, and automatic chat/RAG
  routing remain future work
- the first vector implementation should still use the fixed `docs/` corpus
- a UI selector should wait until there are two real backend retrieval modes

## Revisit Triggers

Revisit this decision if:

- Ollama embeddings are too slow or unreliable for local use
- `nomic-embed-text` performs poorly on the docs corpus
- Qdrant setup becomes too much friction for the local workflow
- Bedrock embeddings become a stronger fit because the project shifts toward an
  AWS-managed retrieval story
- evaluation shows lexical retrieval is sufficient for the intended corpus
- the corpus expands beyond project docs and ADRs into larger document sets
