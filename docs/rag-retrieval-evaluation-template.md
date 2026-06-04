# RAG Retrieval Evaluation Template

Use this template to record one manual RAG comparison pass from the current UI.

The goal is not to prove that vector retrieval is always better. The goal is to
record which retrieval target returns the most useful cited chunks and answer for
this project, with enough detail to reproduce weak results.

For a filled-in illustrative example, see
[rag-retrieval-evaluation-example.md](./rag-retrieval-evaluation-example.md).

## How To Run

1. Start the app:

   ```bash
   ./restart.sh
   ```

2. Open the `RAG` workspace.
3. Confirm the retrieval targets and readiness messages look correct.
4. Type one prompt.
5. Click `Compare Retrieval Targets`.
6. Copy the relevant observations into this template.

Comparison results are not saved as normal RAG session turns. Use `Ask Docs
Corpus` only when you want the selected-target answer persisted in session
history.

Backend env vars such as `RAG_RETRIEVAL_MODE` and `RAG_VECTOR_STORE` are useful
for startup defaults and status checks, but they are not required for normal UI
comparison.

## Run Metadata

- Date/time:
- Tester:
- Commit:
- Browser:
- Provider:
- Model:
- Corpus root:
- Documents:
- Chunks:
- Top K:
- Index rebuilt before test: yes | no
- Notes about startup defaults:

## Target Readiness

Record what the UI showed before running the comparison.

| Retrieval target | Available? | Ready message | Rebuilt before test? | Notes |
| --- | --- | --- | --- | --- |
| Lexical | yes |  | yes |  |
| Vector - In Memory | yes |  | yes |  |
| Vector - Qdrant | yes |  | yes |  |

## Prompt

```text

```

Suggested prompts:

- `How does provider selection work?`
- `Why is MCP separate from the backend?`
- `How are sessions persisted?`
- `What ADR explains the Mermaid architecture diagram?`
- `Where does conversation history live?`
- `What should I check when vector RAG is not working?`

## Per-Target Results

### Lexical

- Status: success | failed | unavailable
- Answer summary:
- Main cited sources:

| Rank | Source | Score | Chunk summary | Relevant? | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 |  |  |  | yes |  |
| 2 |  |  |  | partial |  |
| 3 |  |  |  | no |  |

- Factual alignment: good | acceptable | weak
- Grounding in cited chunks: good | acceptable | weak
- Missing important detail:
- Unsupported or hallucinated claim:
- Runtime/setup issue:

### Vector - In Memory

- Status: success | failed | unavailable
- Answer summary:
- Main cited sources:

| Rank | Source | Score | Chunk summary | Relevant? | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 |  |  |  | yes |  |
| 2 |  |  |  | partial |  |
| 3 |  |  |  | no |  |

- Factual alignment: good | acceptable | weak
- Grounding in cited chunks: good | acceptable | weak
- Missing important detail:
- Unsupported or hallucinated claim:
- Runtime/setup issue:

### Vector - Qdrant

- Status: success | failed | unavailable
- Answer summary:
- Main cited sources:

| Rank | Source | Score | Chunk summary | Relevant? | Notes |
| --- | --- | --- | --- | --- | --- |
| 1 |  |  |  | yes |  |
| 2 |  |  |  | partial |  |
| 3 |  |  |  | no |  |

- Factual alignment: good | acceptable | weak
- Grounding in cited chunks: good | acceptable | weak
- Missing important detail:
- Unsupported or hallucinated claim:
- Runtime/setup issue:

## Comparison Summary

- Best retrieval target for this prompt:
- Best answer for this prompt:
- Best cited sources for this prompt:
- Did vector retrieval improve over lexical? yes | partial | no
- Did Qdrant match in-memory vector behavior? yes | partial | no | not tested
- Was the winning answer clearly grounded in cited chunks? yes | partial | no
- Was the UI status/readiness clear? yes | partial | no

## Observed Tradeoffs

Use concrete observations, not general assumptions.

- Lexical strengths:
- Lexical weaknesses:
- Vector in-memory strengths:
- Vector in-memory weaknesses:
- Qdrant strengths:
- Qdrant weaknesses:

## Failure Or Weak Result Notes

Use this section only if a target failed or gave a weak result.

- Retrieval issue:
- Answer-generation issue:
- Citation issue:
- Runtime/setup issue:
- Reproducible: yes | no
- Exact steps to reproduce:

## Follow-Up Decision

- No action needed:
- Documentation update needed:
- Prompting change needed:
- Chunking/scoring change needed:
- Indexing/readiness change needed:
- UI change needed:
- Should this become an engineering task? yes | no
- Proposed issue/task title:

## Repeated Findings Across Runs

Record only repeated behavior across multiple prompts, providers, or days.

1.
2.
3.

## Related References

- [rag-evaluation-guide.md](./rag-evaluation-guide.md)
- [rag-retrieval-evaluation-example.md](./rag-retrieval-evaluation-example.md)
- [rag-troubleshooting.md](./rag-troubleshooting.md)
- [rag-phase-2-vector-retrieval-design.md](./rag-phase-2-vector-retrieval-design.md)
- [ADR 0014](./adr/0014-add-request-scoped-rag-retrieval-selection-and-comparison.md)
