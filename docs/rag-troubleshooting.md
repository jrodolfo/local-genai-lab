# RAG Troubleshooting

Use this guide when the `RAG` workspace is unavailable, returns weak answers, or
vector retrieval does not behave as expected.

Start with the local status script:

```bash
./status.sh
```

The status output reports whether RAG is enabled, the backend default retrieval
mode, which embedding provider/model are configured, whether Ollama is reachable
when needed, and whether the configured embedding model is installed for vector
mode. The RAG UI also shows per-target readiness for the selectable retrieval
targets.

## Quick Checks

RAG is enabled by default:

```bash
RAG_ENABLED=true ./restart.sh
```

Lexical retrieval is the default and does not need Ollama embeddings:

```bash
RAG_RETRIEVAL_MODE=lexical ./restart.sh
```

Vector retrieval uses Ollama embeddings by default:

```bash
RAG_RETRIEVAL_MODE=vector ./restart.sh
```

If vector mode is enabled, the default embedding model must be available:

```bash
ollama list
ollama pull nomic-embed-text
```

When vector RAG is not working, check these items first:

- run `./status.sh`
- confirm `rag enabled: true`
- in the RAG UI, confirm the selected retrieval target is available
- if using `Vector - In Memory`, confirm Ollama and the embedding model are ready
- if using `Vector - Qdrant`, confirm `qdrant service: ok` and the collection is present
- confirm `ollama service: ok`
- confirm `ollama embedding model: present (nomic-embed-text)`
- run `ollama pull nomic-embed-text` if the embedding model is missing
- click `Rebuild Index` after changing retrieval target, embedding model, or corpus files

Backend env vars such as `RAG_RETRIEVAL_MODE` and `RAG_VECTOR_STORE` still set
startup defaults. They are not required for normal UI switching between
retrieval targets.

## RAG Button Is Missing Or Disabled

Check whether the backend is running with RAG enabled:

```bash
./status.sh
```

Expected status:

```text
rag enabled: true
```

If RAG is disabled, restart with:

```bash
RAG_ENABLED=true ./restart.sh
```

The `RAG` workspace is intentionally separate from normal chat and MCP tool
flows. If the backend starts with `RAG_ENABLED=false`, the UI keeps the route
disabled instead of showing a partially working workspace.

## Vector Mode Cannot Build Or Query The Index

Vector retrieval needs Ollama and the configured embedding model. For normal
manual testing, select the target in the RAG UI and read the selected-target
readiness message. For command-line verification, check:

```bash
./status.sh
```

Expected vector readiness:

```text
rag enabled: true
rag retrieval mode: vector
rag vector store: in-memory
rag embedding provider: ollama
rag embedding model: nomic-embed-text
ollama cli: available
ollama service: ok
ollama embedding model: present (nomic-embed-text)
```

If `RAG_VECTOR_STORE=qdrant` is selected, expected Qdrant readiness is:

```text
rag vector store: qdrant
rag qdrant url: http://localhost:6333
rag qdrant collection: local_genai_lab_docs
qdrant service: ok
qdrant collection: present (points=123)
```

If Qdrant is unavailable, either start in Qdrant mode or start Qdrant with
Docker Compose. The startup script will start the `qdrant` Docker Compose
service automatically when Qdrant mode is configured:

```bash
RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh
./status.sh
```

After Qdrant is reachable, open the RAG workspace, select `Vector - Qdrant`,
and click `Rebuild Index`. Rebuild embeds the docs corpus, recreates the
configured Qdrant collection, and upserts the chunk vectors with citation
payloads for the selected Qdrant target.

If status reports:

```text
qdrant collection: missing (local_genai_lab_docs)
```

then Qdrant is running but the collection has not been rebuilt yet. Select
`Vector - Qdrant` and click `Rebuild Index` in the RAG workspace.

If the backend reports:

```text
Qdrant vector retrieval is selected, but no indexed chunks were found.
```

then Qdrant routing is active, but the collection is empty, missing, or not
queryable. Confirm Qdrant is running, select `Vector - Qdrant`, and click
`Rebuild Index` again. If you want to keep testing without Qdrant, choose
`Vector - In Memory` or `Lexical` in the UI. To make in-memory vector the
backend startup default, use:

```bash
RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=in-memory ./restart.sh
```

If the service is unavailable, start Ollama and rerun `./status.sh`.

If the embedding model is missing, install it:

```bash
ollama pull nomic-embed-text
```

Then restart the app in vector mode:

```bash
RAG_RETRIEVAL_MODE=vector ./restart.sh
```

## Empty Or Stale Index

The RAG page shows index status, document count, chunk count, backend defaults,
and selected retrieval target readiness. If the corpus was changed, the
retrieval target was switched, or the index looks empty, use `Rebuild Index`
from the RAG page.

`Rebuild Index` applies to the selected retrieval target:

- `Lexical` rebuilds the in-memory lexical index
- `Vector - In Memory` embeds the corpus and rebuilds the in-memory vector index
- `Vector - Qdrant` embeds the corpus and recreates/upserts the configured
  Qdrant collection

Rebuild is especially important after:

- switching retrieval targets in the UI
- changing `RAG_RETRIEVAL_MODE`
- changing `RAG_VECTOR_STORE`
- changing `RAG_EMBEDDING_MODEL`
- editing files under the configured docs corpus
- switching between lexical and vector evaluation runs

Changing embedding models requires reindexing because vectors generated by
different embedding models should not be mixed.

## Weak Or Unexpected Answers

First determine whether the problem is retrieval or answer generation.

Check the cited chunks:

- if the cited chunks are irrelevant, the issue is probably retrieval
- if the cited chunks are relevant but the answer is weak, the issue is probably
  answer generation or prompting

For lexical mode, weak retrieval often means the question uses different wording
than the documentation. Try a more direct phrase or compare with vector mode.

For vector mode, weak retrieval can happen when the embedding model retrieves
semantically plausible but source-weak chunks. Rebuild the index, compare the
same prompt in lexical mode, and record repeated failures in the evaluation
notes.

Use `Compare Retrieval Targets` in the RAG UI when you want to compare available
targets with one prompt. Comparison results are not saved as normal RAG session
turns. Use `Ask Docs Corpus` when you want the selected-target answer persisted.

## When To Use Lexical Fallback

Use lexical retrieval when:

- Ollama is not running
- `nomic-embed-text` is not installed
- you want a zero-dependency baseline
- you want to debug whether vector retrieval is actually improving results
- vector mode is blocked by local machine or model setup

Lexical retrieval remains part of the architecture intentionally. It is not just
a temporary placeholder; it is the local baseline for comparing retrieval
quality.

## Related References

- [rag-evaluation-guide.md](./rag-evaluation-guide.md)
- [rag-phase-2-vector-retrieval-design.md](./rag-phase-2-vector-retrieval-design.md)
- [ADR 0012](./adr/0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md)
- [ADR 0013](./adr/0013-use-ollama-embeddings-and-qdrant-for-phase-2-rag-vector-retrieval.md)
