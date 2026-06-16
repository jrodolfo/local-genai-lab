# RAG Evaluation Guide

Use this guide to manually evaluate the current phase-1 RAG workspace.

Current scope:

- separate `RAG` workspace in the frontend
- fixed local corpus from [`docs/`](./)
- in-memory lexical retrieval through `InMemoryLexicalRagRetrievalStore`
- provider-generated answer with cited source chunks

This is intentionally a small, isolated RAG slice. It does not yet include
uploads, vector-backed retrieval, or routing through the main chat/tool flow.

Evaluation-only files such as this guide and the retrieval evaluation template
are excluded from the indexed corpus by default. This keeps manual test prompts
from becoming misleading source chunks.

Related references:

- [architecture.md](./architecture.md)
- [architecture-walkthrough.md](./architecture-walkthrough.md)
- [rag-retrieval-evaluation-template.md](./rag-retrieval-evaluation-template.md)
- [rag-troubleshooting.md](./rag-troubleshooting.md)
- [rag-phase-2-vector-retrieval-design.md](./rag-phase-2-vector-retrieval-design.md)
- [ADR 0012](./adr/0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md)
- [ADR 0013](./adr/0013-use-ollama-embeddings-and-qdrant-for-phase-2-rag-vector-retrieval.md)
- [ADR 0014](./adr/0014-compare-rag-retrieval-targets-without-session-side-effects.md)

## Before You Start

By default, the backend exposes the `RAG` workspace. To hide it explicitly:

```bash
RAG_ENABLED=false ./restart.sh
```

To use it:

1. start the app with `./start.sh` or `./restart.sh`
2. open the frontend
3. switch from `Chat` to `RAG`
4. confirm the status card shows the docs corpus, retrieval mode, and retrieval store

If the workspace is disabled or vector mode does not index, run `./status.sh`
and follow [rag-troubleshooting.md](./rag-troubleshooting.md).

The RAG index is built automatically on the first question. You do not need to
use `Rebuild Index` before every prompt. Rebuild once after changing docs,
retrieval mode, embedding model, vector store settings, or when investigating
stale results.

If you change `RAG_EXCLUDED_SOURCE_PATHS`, rebuild the index before comparing
retrieval results.

## Recommended Prompt Set

Use the same prompt set across Ollama, Bedrock, and Hugging Face when possible:

1. `How does provider selection work?`
2. `Why is MCP separate from the backend?`
3. `How are sessions persisted?`
4. `What ADR explains the Mermaid architecture diagram?`

These questions are good phase-1 checks because the answers should come from
the repository docs and ADRs, not from general model knowledge alone.

Use [rag-retrieval-evaluation-template.md](./rag-retrieval-evaluation-template.md)
when you want to record a repeatable lexical vs vector comparison.

## Manual Lexical Vs Vector Comparison

Use this short pass when you want to compare the dependency-free lexical
baseline against local vector retrieval with the same corpus and prompts.

Use the `Retrieval` selector in the `RAG` workspace to choose `Lexical`, then
run these prompts:

1. `How are sessions persisted?`
2. `Where does conversation history live?`
3. `What should I check when vector RAG is not working?`

Then switch the selector to `Vector - In Memory` or `Vector - Qdrant`, run the
same prompts again, and compare:

- whether the answer is more directly useful
- whether the cited chunks are closer to the question
- whether vector mode finds relevant chunks when the wording differs from the docs
- whether lexical mode remains good enough for exact terminology questions

Record the results in a local copy of
[rag-retrieval-evaluation-template.md](./rag-retrieval-evaluation-template.md).
Those observations should drive the next engineering task, such as chunking,
scoring, answer grounding, or UI comparison support.

### Java version retrieval check

Use this prompt after changing setup, build, or troubleshooting docs:

```text
What is the version of Java for this system?
```

Expected result:

- all retrieval targets should answer `Java 21`
- strong sources should include `README.md`, `docs/testing.md`, or
  `docs/troubleshooting.md`
- lexical retrieval should find the answer because the docs now use the exact
  terms `Java`, `version`, and `Java 21`
- vector retrieval should also find the answer when the wording differs, for
  example `JDK version` or `backend Java baseline`

If lexical misses this prompt while vector retrieval succeeds, the likely issue
is corpus wording, not the retrieval implementation. Add clearer exact terms to
high-signal docs, rebuild the index, and rerun the comparison.

## API-Based Retrieval Comparison

Use the comparison API when you want to compare retrieval targets without saving
diagnostic answers to RAG session history.

Endpoint:

```text
POST /api/rag/compare
```

Example request:

```json
{
  "question": "How are sessions persisted?",
  "provider": "ollama",
  "model": "llama3:8b",
  "retrievalTargets": ["lexical", "vector:in-memory", "vector:qdrant"]
}
```

If `retrievalTargets` is omitted, the backend compares all supported targets.
Each result includes:

- `retrievalTarget`
- `success`
- `answer` when successful
- `error` when that target failed
- `sources`
- `ragRetrieval`
- `ragTiming`

This endpoint is useful when evaluating retrieval behavior or writing automated
checks. For normal RAG use, submit a question through the RAG workspace or
`POST /api/rag/query`, because that path saves the answer to the RAG session.

## What To Evaluate

### Answer quality

Check whether the answer:

- is factually aligned with the repository
- stays focused on the actual question
- avoids generic filler
- reflects the current architecture instead of hallucinated features

### Citation quality

Check whether the cited chunks:

- come from the right files
- are clearly relevant to the answer
- help you verify the answer quickly
- do not point to unrelated ADRs or sections

### Retrieval quality

Check whether the retrieved chunks:

- reflect the best matching docs for the prompt
- include the relevant ADR when the question is decision-oriented
- avoid obviously lower-signal chunks when better ones exist

## Lexical Search Vs Vector Search

Lexical search matches text by words.

Vector search matches text by meaning.

In the current phase-1 RAG workspace, `lexical retrieval` means:

- the query is split into tokens or words
- each document chunk is split into tokens or words
- the system scores chunks based on word overlap and frequency
- it works well when the user's question uses similar terms to the docs

For example:

- query: `How are sessions persisted?`
- doc chunk: `Sessions are stored as local JSON files`
- lexical search can find it because `sessions` and related terms overlap

`Vector search` means:

- the query is converted into an embedding, which is a numeric representation of meaning
- each document chunk is also converted into an embedding
- the system compares vectors using similarity math
- it can find relevant chunks even when the wording is different

For example:

- query: `Where does conversation history live?`
- doc chunk: `Sessions are stored as local JSON files`
- lexical search may miss it because the exact words differ
- vector search is more likely to match it because the meaning is similar

Tradeoff:

- lexical search is simple, fast, dependency-free, and easy to explain
- vector search is usually better for semantic matching, but needs embeddings and often a vector database or vector index

For this lab, lexical search is valuable because it is a clean baseline. The
backend can also run experimental local vector retrieval when started with
`RAG_RETRIEVAL_MODE=vector`, so lexical and vector retrieval can be compared
directly against the same corpus.

## Retrieval Modes

Default mode:

- `lexical`
- implemented by `InMemoryLexicalRagRetrievalStore`
- dependency-free baseline for local docs and ADRs

Experimental mode:

- `vector`
- backed by Ollama embeddings and an in-memory vector store
- useful when questions and docs use different wording but similar meaning

The intended shape is comparison, not replacement. Lexical retrieval should
remain available as a lab baseline and fallback even when vector-backed
retrieval is enabled locally.

The UI should expose a retrieval-mode selector only after at least two real
retrieval implementations are mature enough for normal UI switching. Until then,
start vector mode through backend configuration and use the status card to
confirm the active mode and store.

For the phase-2 vector retrieval direction, including Qdrant as the first
external vector database candidate, see
[rag-phase-2-vector-retrieval-design.md](./rag-phase-2-vector-retrieval-design.md).

### Provider differences

Compare whether Ollama, Bedrock, and Hugging Face differ in:

- answer clarity
- faithfulness to the retrieved chunks
- willingness to cite the right material
- tendency to over-generalize beyond the local docs corpus

## What A Good Result Looks Like

A good phase-1 result is:

- the answer is mostly grounded in the local docs corpus
- the cited chunks are recognizably relevant
- the answer mentions the right ADR or architecture file when appropriate
- the provider adds synthesis without drifting away from the retrieved material

Phase 1 does not need perfect semantic retrieval. It needs a small, honest,
useful docs-grounded workflow with clear citations.

## Problems Worth Recording

Record repeated issues such as:

- the wrong ADR is cited consistently
- the retrieved chunks are too broad or too shallow
- one provider ignores the retrieved context more than the others
- answers mention features the repo does not actually implement
- citations are technically present but not useful

Do not overreact to one-off weak answers. Promote only repeated problems into
follow-up engineering work.

## Likely Follow-Up Directions

If the main issue is retrieval quality:

- improve chunking
- tune `top-k`
- refine lexical scoring or add a second retrieval mode later

If the main issue is answer grounding:

- tighten the RAG answer prompt
- make source use more explicit

If the main issue is corpus coverage:

- expand beyond `docs/` in a later phase

If the main issue is scale or retrieval accuracy:

- evaluate embeddings
- evaluate vector-backed retrieval in a later phase
