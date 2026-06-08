# RAG Troubleshooting

Use this guide when the `RAG` workspace is unavailable, returns weak answers, or
vector retrieval does not behave as expected.

Start with the local status script:

```bash
./status.sh
```

The status output reports whether RAG is enabled, which retrieval mode is active,
which embedding provider/model are configured, whether Ollama is reachable when
needed, and whether the configured embedding model is installed for vector mode.

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
- confirm `rag retrieval mode: vector`
- confirm `rag vector store: in-memory` or `rag vector store: qdrant`
- if `rag vector store: qdrant`, confirm `qdrant service: ok`
- confirm `ollama service: ok`
- confirm `ollama embedding model: present (nomic-embed-text)`
- run `ollama pull nomic-embed-text` if the embedding model is missing
- click `Rebuild Index` after changing retrieval mode, embedding model, or corpus files

## Index Status Looks Empty Before First Use

The RAG workspace uses lazy indexing. After a fresh backend start, the UI may
show:

```text
Status: will index on first question
Documents: not loaded yet
Chunks: not loaded yet
```

This does not mean the docs corpus is empty and it does not mean RAG is broken.
The first RAG question builds the in-memory index automatically, then the UI
refreshes status and shows the real document and chunk counts.

`Rebuild Index` is optional for normal first use. Use it when:

- docs under the configured corpus changed
- retrieval mode changed
- embedding model changed
- vector store settings changed
- answers look stale or troubleshooting requires a clean index

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

Vector retrieval needs Ollama and the configured embedding model. Check:

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

If Qdrant is unavailable, restart in Qdrant mode. The startup script will start
the `qdrant` Docker Compose service automatically:

```bash
RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh
./status.sh
```

After Qdrant is reachable, the first RAG question can build the configured
Qdrant index automatically. You can also click `Rebuild Index` before asking if
you want to prebuild the collection. In Qdrant mode, rebuild embeds the docs
corpus, recreates the configured Qdrant collection, and upserts the chunk
vectors with citation payloads.

If status reports:

```text
qdrant collection: missing (local_genai_lab_docs)
```

then Qdrant is running but the collection has not been rebuilt yet. Click
`Rebuild Index` in the RAG workspace.

If the backend reports:

```text
Qdrant vector retrieval is selected, but no indexed chunks were found.
```

then Qdrant routing is active, but the collection is empty, missing, or not
queryable. Confirm Qdrant is running and click `Rebuild Index` again. If you
want to keep testing without Qdrant, use the in-memory vector path:

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

The RAG page shows index status, document count, chunk count, retrieval mode, and
retrieval store. A fresh backend can show `will index on first question` with
`not loaded yet` counts before the first RAG query; that is expected lazy
indexing. If the corpus was changed, the retrieval mode was switched, or answers
look stale, use `Rebuild Index` from the RAG page.

Rebuild is especially important after:

- changing `RAG_RETRIEVAL_MODE`
- changing `RAG_EMBEDDING_MODEL`
- editing files under the configured docs corpus
- switching between lexical and vector evaluation runs

Changing embedding models requires reindexing because vectors generated by
different embedding models should not be mixed.

## RAG Technical Details

Each RAG answer can show a collapsed `Technical Details` section. These fields
explain how that answer was retrieved and how long each backend phase took.
They are useful for debugging and comparing retrieval modes, but they do not
prove that an answer is correct. Always review the cited chunks and the answer
grounding together.

- `Retrieval mode`: the high-level retrieval strategy used for the answer.
  `Lexical` matches terms from the question against indexed text. `Vector`
  embeds the question and searches by vector similarity.
- `Retrieval target`: the stable selector for the exact retrieval path. Examples
  include `lexical`, `vector:in-memory`, and `vector:qdrant`.
- `Retrieval store`: the retrieval implementation family used by the backend,
  such as `In memory` for lexical search or `In memory vector` for local vector
  search.
- `Vector store`: the configured vector storage backend. In lexical mode this
  may still show the configured value, usually `In memory`, even though vector
  search was not used for that answer.
- `Top K`: the maximum number of source chunks requested from retrieval before
  the answer prompt is built.
- `Embedding provider`: the service used to generate embeddings for vector
  retrieval, for example `Ollama`. This is shown only when vector metadata is
  present.
- `Embedding model`: the embedding model used for vector retrieval, for example
  `nomic-embed-text`. This is shown only when vector metadata is present.
- `Retrieval duration`: backend time spent finding source chunks. Fast local
  lexical retrieval can show `<1 ms`, which means the measured duration rounded
  below one millisecond.
- `Provider duration`: backend time spent waiting for the selected LLM provider
  to generate the answer after retrieval completed.
- `Backend total`: total backend request time for the RAG answer, including
  retrieval, provider generation, metadata handling, and session persistence.

Use these fields to answer operational questions such as:

- did this answer use lexical, in-memory vector, or Qdrant retrieval?
- is provider generation slow, or is retrieval slow?
- did the answer use the expected `RAG_TOP_K` and embedding model?
- did switching retrieval modes actually affect the saved answer metadata?

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
