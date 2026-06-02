# RAG Evaluation Guide

Use this guide to manually evaluate the current phase-1 RAG workspace.

Current scope:

- separate `RAG` workspace in the frontend
- fixed local corpus from [`docs/`](./)
- in-memory lexical retrieval through `InMemoryLexicalRagRetrievalStore`
- provider-generated answer with cited source chunks

This is intentionally a small, isolated RAG slice. It does not yet include
uploads, vector-backed retrieval, or routing through the main chat/tool flow.

Related references:

- [architecture.md](./architecture.md)
- [architecture-walkthrough.md](./architecture-walkthrough.md)
- [ADR 0012](./adr/0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md)

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

If needed, use `Rebuild index` before testing.

## Recommended Prompt Set

Use the same prompt set across Ollama, Bedrock, and Hugging Face when possible:

1. `How does provider selection work?`
2. `Why is MCP separate from the backend?`
3. `How are sessions persisted?`
4. `What ADR explains the Mermaid architecture diagram?`

These questions are good phase-1 checks because the answers should come from
the repository docs and ADRs, not from general model knowledge alone.

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
backend exposes retrieval through `RagRetrievalStore`, so vector search can be
added later as a second retrieval mode and compared directly with the current
lexical implementation.

## Future Retrieval Modes

Current mode:

- `lexical`
- implemented by `InMemoryLexicalRagRetrievalStore`
- dependency-free baseline for local docs and ADRs

Possible future mode:

- `vector`
- backed by embeddings and a vector index or vector database
- useful when questions and docs use different wording but similar meaning

The intended future shape is comparison, not replacement. Lexical retrieval
should remain available as a lab baseline and fallback even if vector-backed
retrieval is added later.

The UI should expose a retrieval-mode selector only after at least two real
retrieval implementations exist. Until then, the status card reports the active
mode and store without offering a switch that cannot do useful work.

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
