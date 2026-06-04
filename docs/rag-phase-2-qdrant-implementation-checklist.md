# RAG Phase 2 Qdrant Implementation Checklist

This checklist turns the phase-2 vector database design into concrete
implementation tasks.

It is intentionally scoped to the smallest useful Qdrant slice:

- keep the fixed `docs/` corpus
- keep lexical retrieval available
- keep the current in-memory vector retrieval available
- add Qdrant as an opt-in vector store
- do not add uploads, MCP retrieval, report ingestion, or automatic chat/RAG
  routing

Related references:

- [rag-phase-2-vector-retrieval-design.md](./rag-phase-2-vector-retrieval-design.md)
- [rag-troubleshooting.md](./rag-troubleshooting.md)
- [ADR 0013](./adr/0013-use-ollama-embeddings-and-qdrant-for-phase-2-rag-vector-retrieval.md)

## Current Starting Point

Already implemented:

- `EmbeddingService`
- `OllamaEmbeddingService`
- `InMemoryLexicalRagRetrievalStore`
- `InMemoryVectorRagRetrievalStore`
- `RagVectorIndexingService`
- `RagVectorRetrievalService`
- `RagStatusResponse.retrievalStore`
- `RAG_VECTOR_STORE`
- `RAG_QDRANT_URL`
- `RAG_QDRANT_COLLECTION`
- Qdrant service in Docker Compose
- Qdrant configuration fields in `/api/rag/status`
- live Qdrant reachability in `/api/rag/status`
- `status.sh` RAG/Ollama readiness checks
- `status.sh` Qdrant configuration output when Qdrant mode is selected
- `status.sh` live Qdrant reachability when Qdrant mode is selected
- RAG UI status for retrieval mode and store
- RAG UI Qdrant reachability messages when Qdrant mode is selected
- Qdrant client boundary with collection, upsert, and search operations
- Qdrant-backed vector retrieval store and routing behind `RAG_VECTOR_STORE=qdrant`

Not implemented yet:

- Qdrant indexing/rebuild integration
- optional Qdrant integration tests

## Phase 2 Slice 1: Configuration

Backend tasks:

- Keep the vector store setting in `RagProperties`.
- Current default: `RAG_VECTOR_STORE=in-memory`.
- Current supported values for the first Qdrant slice: `in-memory`, `qdrant`.
- Keep `RAG_QDRANT_URL`.
- Current default: `http://localhost:6333`.
- Keep `RAG_QDRANT_COLLECTION`.
- Current default: `local_genai_lab_docs`.
- Keep `RAG_RETRIEVAL_MODE=lexical` as the default retrieval mode.

Acceptance criteria:

- Normal startup still works without Docker and without Qdrant.
- `RAG_RETRIEVAL_MODE=lexical ./restart.sh` does not require Qdrant.
- `RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=in-memory ./restart.sh`
  preserves the current vector behavior.
- `RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh` selects the
  future Qdrant path explicitly.

## Phase 2 Slice 2: Docker Compose

Docker tasks:

- Keep the `qdrant` service in `docker-compose.yml`.
- Keep Qdrant exposed on `6333`.
- Keep a named volume for local Qdrant data.
- Do not make the default backend test suite depend on the Qdrant container.

Expected local command:

```bash
docker compose up -d qdrant
RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh
```

Acceptance criteria:

- `docker compose up -d qdrant` starts Qdrant without starting the whole app.
- Existing `docker compose up --build` still works.
- Documentation states that Qdrant is optional and only needed for
  `RAG_VECTOR_STORE=qdrant`.

## Phase 2 Slice 3: Qdrant Client Boundary

Backend tasks:

- Keep the Qdrant client boundary under the RAG package.
- Keep HTTP request/response details isolated from retrieval services.
- Keep collection existence checks.
- Keep collection recreation for rebuild.
- Keep point upsert with payload metadata.
- Keep vector search with top-k.
- Keep converting Qdrant failures into project-specific exceptions or status objects.

Suggested package shape:

```text
backend/src/main/java/net/jrodolfo/llm/rag/qdrant/
  QdrantClient.java
  QdrantClientException.java
  QdrantPoint.java
  QdrantPointPayload.java
  QdrantSearchResult.java
```

Acceptance criteria:

- Unit tests can verify request construction without a running Qdrant container.
- Qdrant-specific DTOs do not leak into controller responses unless intentional.
- Connection failures produce actionable messages.

## Phase 2 Slice 4: Qdrant-Backed Store

Backend tasks:

- Keep the Qdrant-backed vector store implementation.
- Keep `InMemoryVectorRagRetrievalStore` unchanged.
- Keep routing vector retrieval by `RAG_VECTOR_STORE`.
- Keep using Qdrant only when `RAG_RETRIEVAL_MODE=vector` and
  `RAG_VECTOR_STORE=qdrant`.
- Do not silently fall back from Qdrant to lexical retrieval.

Suggested class:

```text
backend/src/main/java/net/jrodolfo/llm/rag/store/QdrantVectorRagRetrievalStore.java
```

Acceptance criteria:

- `lexical` mode still uses `InMemoryLexicalRagRetrievalStore`.
- `vector` plus `in-memory` still uses `InMemoryVectorRagRetrievalStore`.
- `vector` plus `qdrant` uses Qdrant.
- Qdrant unavailable is reported clearly instead of hidden by fallback.
- Until Qdrant indexing is implemented, Qdrant mode reports a clear
  index-not-available error instead of silently using another store.

## Phase 2 Slice 5: Index Rebuild

Backend tasks:

- Reuse existing chunking and embedding services.
- On `Rebuild Index`, recreate or clear the configured Qdrant collection.
- Store each chunk vector with citation and debugging payload.
- Include embedding provider and embedding model in the payload.
- Treat changing `RAG_EMBEDDING_MODEL` as requiring rebuild.

Payload fields:

- `source_path`
- `chunk_id`
- `title`
- `text`
- `corpus_root`
- `embedding_provider`
- `embedding_model`
- `indexed_at`
- `content_hash`

Acceptance criteria:

- Rebuild creates the collection if it does not exist.
- Rebuild makes the collection queryable immediately after completion.
- Rebuild returns document count, chunk count, retrieval mode, and vector store.
- Rebuild failure leaves a clear error in the API/UI.

## Phase 2 Slice 6: Status API And Scripts

Backend tasks:

- Keep Qdrant-specific fields in `/api/rag/status` when selected.
- Include configured vector store.
- Include Qdrant URL.
- Include collection name.
- Keep Qdrant reachability.
- Add collection presence later when the Qdrant client boundary exists.
- Include configured embedding model and indexed embedding model when available.

Script tasks:

- Keep `./status.sh` printing `rag vector store`.
- Keep checking Qdrant only when `RAG_RETRIEVAL_MODE=vector` and
  `RAG_VECTOR_STORE=qdrant`.
- Keep lexical status fast and independent from Qdrant.
- Keep in-memory vector status focused on Ollama and the embedding model.

Future `./status.sh` example:

```text
rag enabled: true
rag retrieval mode: vector
rag vector store: qdrant
rag embedding provider: ollama
rag embedding model: nomic-embed-text
qdrant url: http://localhost:6333
qdrant service: ok
qdrant collection: present (local_genai_lab_docs)
```

Acceptance criteria:

- `./status.sh` does not fail if Qdrant is not installed.
- `./status.sh` tells the user exactly what is missing in Qdrant mode.
- `/api/rag/status` gives the frontend enough information for actionable UI
  messages.

## Phase 2 Slice 7: Frontend

Frontend tasks:

- Keep the RAG page interaction simple.
- Show active retrieval mode and vector store compactly.
- Show Qdrant-specific readiness only when Qdrant is selected.
- Show clear action messages for unavailable Qdrant or missing collection.
- Keep lexical and in-memory vector UI behavior unchanged.

Good messages:

- `Qdrant is not reachable at http://localhost:6333. Start it and rebuild the index.`
- `The Qdrant collection is missing. Click Rebuild Index.`
- `The index was built with a different embedding model. Rebuild the index.`

Acceptance criteria:

- The user can distinguish lexical, in-memory vector, and Qdrant vector mode.
- Qdrant failures do not look like generic backend failures.
- The UI does not imply Qdrant is used when the current store is in-memory.

## Phase 2 Slice 8: Tests

Default tests:

- must not require Docker
- must not require Qdrant
- must keep current lexical and in-memory vector behavior covered

Backend unit tests:

- `RagProperties` binds vector store and Qdrant settings.
- Qdrant client builds expected requests.
- Qdrant payload mapping preserves citation fields.
- Vector store routing selects in-memory or Qdrant correctly.
- Qdrant unavailable produces an actionable status/error.

Backend optional integration tests:

- run only behind an explicit profile or environment flag
- verify collection creation
- verify point upsert
- verify vector search returns expected chunks
- verify rebuild replaces stale collection contents

Frontend tests:

- render Qdrant status fields
- render Qdrant unavailable message
- preserve lexical status rendering
- preserve in-memory vector status rendering

Script tests:

- lexical mode does not check Qdrant
- in-memory vector mode does not check Qdrant
- Qdrant vector mode reports service unavailable when Qdrant is down
- Qdrant vector mode reports service ok when Qdrant is reachable

## Non-Goals For This Slice

Do not include:

- document uploads
- report artifact ingestion
- MCP retrieval
- automatic chat/RAG routing
- multi-user indexing
- cloud vector stores
- Bedrock embeddings
- deleting lexical retrieval
- deleting in-memory vector retrieval

## Manual Verification Flow

Baseline lexical:

```bash
RAG_RETRIEVAL_MODE=lexical ./restart.sh
./status.sh
```

Baseline in-memory vector:

```bash
RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=in-memory ./restart.sh
./status.sh
```

Future Qdrant vector:

```bash
docker compose up -d qdrant
RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh
./status.sh
```

Use the same prompts from [rag-evaluation-guide.md](./rag-evaluation-guide.md)
to compare citation quality across modes.

## Completion Criteria

The Qdrant slice is complete when:

- default app startup works without Qdrant
- lexical retrieval still works
- in-memory vector retrieval still works
- Qdrant vector retrieval works when explicitly selected
- Qdrant status is visible in `/api/rag/status`
- Qdrant status is visible in `./status.sh`
- the UI gives actionable Qdrant errors
- default CI remains independent from Docker/Qdrant
- optional Qdrant integration tests are documented and runnable
