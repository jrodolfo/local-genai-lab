# RAG Evaluation Guide

Use this guide to manually evaluate the current phase-1 RAG workspace.

Current scope:

- separate `RAG` workspace in the frontend
- fixed local corpus from [`docs/`](./)
- in-memory lexical retrieval through `InMemoryLexicalRagRetrievalStore`
- in-memory vector retrieval through Ollama embeddings
- optional Qdrant vector retrieval when Qdrant is available and indexed
- request-scoped retrieval selection in the UI
- side-by-side retrieval comparison for available targets
- provider-generated answer with cited source chunks

This is intentionally a small, isolated RAG slice. It does not yet include
uploads, report-corpus ingestion, automatic routing through the main chat/tool
flow, or MCP retrieval.

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

## Before You Start

By default, the backend exposes the `RAG` workspace. To hide it explicitly:

```bash
RAG_ENABLED=false ./restart.sh
```

To use it:

1. start the app with `./start.sh` or `./restart.sh`
2. open the frontend
3. switch from `Chat` to `RAG`
4. confirm the status card shows the docs corpus, backend defaults, and selected retrieval target readiness
5. use the `Retrieval` selector to choose `Lexical`, `Vector - In Memory`, or `Vector - Qdrant` when available

If the workspace is disabled or vector mode does not index, run `./status.sh`
and follow [rag-troubleshooting.md](./rag-troubleshooting.md).

If needed, use `Rebuild Index` before testing. Rebuild applies to the currently
selected retrieval target.

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

## Recommended Retrieval Comparison

Use this pass when you want to compare lexical, in-memory vector, and Qdrant
retrieval with the same corpus and prompt.

Normal UI workflow:

1. start the app with `./restart.sh`
2. open the `RAG` workspace
3. make sure the available retrieval targets look correct
4. type one of these prompts:

   - `How are sessions persisted?`
   - `Where does conversation history live?`
   - `What should I check when vector RAG is not working?`

5. click `Compare Retrieval Targets`
6. compare the answer, cited chunks, and source scores across the cards

Compare:

- whether the answer is more directly useful
- whether the cited chunks are closer to the question
- whether vector mode finds relevant chunks when the wording differs from the docs
- whether lexical mode remains good enough for exact terminology questions
- whether Qdrant behaves like the in-memory vector store after the same index rebuild

Record the results in a local copy of
[rag-retrieval-evaluation-template.md](./rag-retrieval-evaluation-template.md).
Those observations should drive the next engineering task, such as chunking,
scoring, answer grounding, or retrieval target readiness.

Comparison results are not saved as normal RAG conversation turns. Use
`Ask Docs Corpus` when you want a single selected-target answer saved in the
RAG session history.

Advanced backend-default workflow:

```bash
RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh
```

Use backend defaults when you specifically want startup scripts and `status.sh`
to treat Qdrant as the active configured vector store. For normal evaluation,
prefer the UI selector and comparison button.

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
backend can also run experimental local vector retrieval and Qdrant-backed
vector retrieval. Use the RAG UI retrieval selector or `Compare Retrieval
Targets` button to compare them directly against the same corpus.

## Retrieval Modes

Default mode:

- `lexical`
- implemented by `InMemoryLexicalRagRetrievalStore`
- dependency-free baseline for local docs and ADRs

Experimental mode:

- `vector`
- backed by Ollama embeddings and an in-memory vector store
- useful when questions and docs use different wording but similar meaning

Optional vector database mode:

- `vector` with `qdrant`
- backed by Ollama embeddings and a local Qdrant collection
- useful for evaluating a realistic vector database path while keeping the same
  fixed docs corpus

The intended shape is comparison, not replacement. Lexical retrieval should
remain available as a lab baseline and fallback even when vector-backed
retrieval is enabled locally.

The UI now exposes request-scoped retrieval target selection. Saved answers show
which retrieval target produced them, and comparison runs show available targets
side by side without persisting those runs as session turns.

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
