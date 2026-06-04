# RAG Retrieval Evaluation Example

This is an illustrative example of how to fill out
[rag-retrieval-evaluation-template.md](./rag-retrieval-evaluation-template.md).
It is not a benchmark result.

Exact answers, source order, and scores can change with provider/model choice,
corpus edits, chunking changes, index state, and embedding model behavior.

## Run Metadata

- Date/time: 2026-06-04 15:00 local
- Tester: example
- Commit: example-sha
- Browser: Chrome
- Provider: Ollama
- Model: llama3:8b
- Corpus root: `docs/`
- Documents: example value
- Chunks: example value
- Top K: 4
- Index rebuilt before test: yes
- Notes about startup defaults: started with `./restart.sh`; retrieval targets selected in the UI

## Target Readiness

| Retrieval target | Available? | Ready message | Rebuilt before test? | Notes |
| --- | --- | --- | --- | --- |
| Lexical | yes | Ready. Uses the zero-dependency lexical index. | yes | Baseline target |
| Vector - In Memory | yes | Ready. Uses Ollama embeddings and an in-memory vector index. | yes | Requires Ollama and `nomic-embed-text` |
| Vector - Qdrant | yes | Ready. Qdrant collection has indexed points. | yes | Requires Qdrant collection rebuild |

## Prompt

```text
How are sessions persisted?
```

## Per-Target Results

### Lexical

- Status: success
- Answer summary: Sessions are stored as local JSON files so they can be reopened, exported, and imported.
- Main cited sources: ADR 0006 and session-related documentation.

| Rank | Source | Score | Chunk summary | Relevant? | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 | `adr/0006-persist-sessions-as-local-json-files.md` | 0.83 | Decision to persist sessions as local JSON files | yes | Strong direct match |
| 2 | `architecture.md` | 0.61 | Session persistence and local JSON storage | yes | Useful supporting context |
| 3 | `testing.md` | 0.40 | Session import/export verification | partial | Useful but less central |

- Factual alignment: good
- Grounding in cited chunks: good
- Missing important detail: none
- Unsupported or hallucinated claim: none
- Runtime/setup issue: none

### Vector - In Memory

- Status: success
- Answer summary: Conversation sessions live in backend-managed local JSON files under the configured sessions directory.
- Main cited sources: ADR 0006 and architecture/session docs.

| Rank | Source | Score | Chunk summary | Relevant? | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 | `adr/0006-persist-sessions-as-local-json-files.md` | 0.78 | Local JSON file persistence decision | yes | Strong semantic match |
| 2 | `architecture.md` | 0.66 | Sessions and local persistence responsibilities | yes | Good supporting chunk |
| 3 | `rag-evaluation-guide.md` | 0.42 | Manual prompt list mentioning sessions | no | Lower-signal citation if returned |

- Factual alignment: good
- Grounding in cited chunks: acceptable
- Missing important detail: configured directory would make the answer more concrete
- Unsupported or hallucinated claim: none
- Runtime/setup issue: none

### Vector - Qdrant

- Status: success
- Answer summary: Sessions are persisted in local JSON session files managed by the backend session store.
- Main cited sources: ADR 0006 and architecture/session docs.

| Rank | Source | Score | Chunk summary | Relevant? | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 | `adr/0006-persist-sessions-as-local-json-files.md` | 0.79 | Accepted decision for local JSON session files | yes | Equivalent to in-memory vector result |
| 2 | `architecture.md` | 0.64 | Session load/export/import architecture | yes | Useful operational context |
| 3 | `docs/testing.md` | 0.44 | Session loading/export manual tests | partial | Related but not the primary source |

- Factual alignment: good
- Grounding in cited chunks: good
- Missing important detail: none
- Unsupported or hallucinated claim: none
- Runtime/setup issue: none

## Comparison Summary

- Best retrieval target for this prompt: Lexical
- Best answer for this prompt: Vector - Qdrant
- Best cited sources for this prompt: Lexical and Vector - Qdrant tied
- Did vector retrieval improve over lexical? partial
- Did Qdrant match in-memory vector behavior? yes
- Was the winning answer clearly grounded in cited chunks? yes
- Was the UI status/readiness clear? yes

## Observed Tradeoffs

- Lexical strengths: direct match on `sessions` and `persisted`; found ADR 0006 cleanly.
- Lexical weaknesses: may be less effective if the prompt uses different wording, such as “Where does conversation history live?”
- Vector in-memory strengths: good semantic match and useful phrasing.
- Vector in-memory weaknesses: may include a lower-signal evaluation-guide chunk if the corpus contains similar prompt wording.
- Qdrant strengths: similar semantic quality to in-memory vector with durable vector storage.
- Qdrant weaknesses: requires Qdrant readiness and a rebuilt collection.

## Failure Or Weak Result Notes

- Retrieval issue: none for this prompt.
- Answer-generation issue: none for this prompt.
- Citation issue: watch for low-signal evaluation-guide citations in vector results.
- Runtime/setup issue: none.
- Reproducible: not applicable.
- Exact steps to reproduce: start with `./restart.sh`, open `RAG`, run `Compare Retrieval Targets` for the prompt above.

## Follow-Up Decision

- No action needed: yes
- Documentation update needed: no
- Prompting change needed: no
- Chunking/scoring change needed: no
- Indexing/readiness change needed: no
- UI change needed: no
- Should this become an engineering task? no
- Proposed issue/task title: none

## Repeated Findings Across Runs

1. Do not promote one weak citation into an engineering task unless it repeats.
2. Lexical is strong when the prompt uses exact project terminology.
3. Vector targets are most useful when the prompt uses different wording than the docs.
