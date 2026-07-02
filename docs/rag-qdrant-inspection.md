# RAG Qdrant Inspection

This guide shows how to inspect the local Qdrant database used by the
`Vector - Qdrant` RAG target.

Qdrant stores vector embeddings for chunks from the local docs corpus. The
backend still owns the natural-language flow:

```text
docs chunk -> Ollama embedding vector -> Qdrant point -> payload for citation
user question -> Ollama embedding vector -> Qdrant search -> cited chunks -> answer provider
```

Qdrant does not answer natural-language questions by itself. It stores vectors
and payloads, then returns the nearest stored points when the backend sends a
query vector.

## Defaults

Local defaults:

- Qdrant URL: `http://localhost:6333`
- Collection: `local_genai_lab_docs`
- Vector size: `768` for `nomic-embed-text`
- Distance metric: `Cosine`

Start the local runtime first:

```bash
./scripts/restart.sh
./scripts/status.sh
```

If Qdrant is not running, `./scripts/restart.sh` tries to start the local Docker Compose
service when RAG is enabled.

## Browser-Friendly Checks

These endpoints are simple `GET` requests, so they work directly in a browser.

List collections:

```text
http://localhost:6333/collections
```

Expected shape:

```json
{
  "result": {
    "collections": [
      {
        "name": "local_genai_lab_docs"
      }
    ]
  },
  "status": "ok"
}
```

Inspect the RAG collection:

```text
http://localhost:6333/collections/local_genai_lab_docs
```

Useful fields:

- `status: green` means Qdrant considers the collection healthy.
- `points_count` is the number of stored RAG chunks.
- `vectors.size` is the embedding vector dimension.
- `distance: Cosine` is the similarity metric used for nearest-neighbor search.
- `optimizer_status: ok` means Qdrant background maintenance is healthy.
- `indexed_vectors_count` can be `0` for a small local collection and still be
  fine. For this project, `points_count` is the important quick check that data
  was stored.

Example:

```json
{
  "result": {
    "status": "green",
    "optimizer_status": "ok",
    "indexed_vectors_count": 0,
    "points_count": 205,
    "config": {
      "params": {
        "vectors": {
          "size": 768,
          "distance": "Cosine"
        }
      }
    }
  },
  "status": "ok"
}
```

## Curl Checks

The next endpoints require `POST` with a JSON body, so they are better with
`curl`, Postman, HTTPie, or another API client.

Count points:

```bash
curl http://localhost:6333/collections/local_genai_lab_docs/points/count \
  -H 'Content-Type: application/json' \
  -d '{}'
```

Scroll sample stored chunks with payloads and without vectors:

```bash
curl http://localhost:6333/collections/local_genai_lab_docs/points/scroll \
  -H 'Content-Type: application/json' \
  -d '{
    "limit": 5,
    "with_payload": true,
    "with_vector": false
  }'
```

This is the most useful inspection command because it shows what the RAG index
stored for citations and debugging.

Expected payload fields can include:

- `source_path`
- `chunk_id`
- `title`
- `text`
- `corpus_root`
- `embedding_provider`
- `embedding_model`
- `indexed_at`
- `content_hash`

Example shortened output:

```json
{
  "result": {
    "points": [
      {
        "id": "00cf76a2-144b-3044-a244-859078ad8467",
        "payload": {
          "source_path": "rag-troubleshooting.md",
          "chunk_id": "rag-troubleshooting.md#chunk-5",
          "title": "RAG Troubleshooting",
          "text": "Vector retrieval needs Ollama and the configured embedding model...",
          "corpus_root": "<repo>/docs",
          "embedding_provider": "ollama",
          "embedding_model": "nomic-embed-text",
          "indexed_at": "2026-06-16T16:29:19.435492Z",
          "content_hash": "603af9f3d10ee9ec56bb85c7afed17db1fb235d604ddc6e561a2c23a418435da"
        }
      },
      {
        "id": "06d6e3cd-af88-3a5b-852f-16db8c259d65",
        "payload": {
          "source_path": "rag-phase-2-vector-retrieval-design.md",
          "chunk_id": "rag-phase-2-vector-retrieval-design.md#chunk-6",
          "title": "RAG Phase 2 Vector Retrieval Design",
          "text": "Qdrant is implemented as a second vector store option...",
          "embedding_provider": "ollama",
          "embedding_model": "nomic-embed-text"
        }
      }
    ]
  },
  "status": "ok"
}
```

The full `text` field can be long because it contains the indexed chunk used for
retrieval and citation. Use `"with_vector": false` during normal inspection so
the response stays readable.

## Why Some URLs Work In The Browser

Browser address bars send `GET` requests. That is why these work:

- `/collections`
- `/collections/local_genai_lab_docs`

Counting and scrolling points require a request body, so they use `POST`.
Browsers do not send arbitrary JSON bodies from the address bar, which is why
`curl` or an API client is better for those operations.

## Direct Semantic Search

Qdrant semantic search requires a vector, not plain text. A natural-language
question must first be converted into an embedding using the same embedding
model used for indexing.

The backend already does this for the RAG flow:

```text
question text -> Ollama nomic-embed-text embedding -> Qdrant search
```

For normal use, prefer the RAG page or the backend RAG API. Use direct Qdrant
inspection when you want to understand what was indexed, verify point counts,
or debug collection readiness.

## Common Findings

`collections` is empty:

- Qdrant is running, but the RAG collection has not been created.
- Click `Rebuild Index` in the RAG page or ask a RAG question using
  `Vector - Qdrant`.

`points_count` is `0`:

- The collection exists but no chunks were stored.
- Click `Rebuild Index` and check the backend log if it stays empty.

Qdrant is unreachable:

- Run `./scripts/status.sh`.
- Restart with `./scripts/restart.sh`.
- Confirm your Docker UI or `docker compose ps qdrant` shows the `llm-qdrant`
  container running.
