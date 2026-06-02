# RAG Retrieval Evaluation Template

Use this template to compare RAG retrieval modes against the same local docs
corpus and prompt set.

The goal is not to prove that vector retrieval is always better. The goal is to
record whether a retrieval mode returns better source chunks for this project,
with enough detail to reproduce weak results.

## Metadata

- Date:
- Tester:
- Commit:
- Corpus root:
- RAG enabled:
- Retrieval mode:
- Retrieval store:
- Provider:
- Model:
- Top K:
- Embedding provider, if vector:
- Embedding model, if vector:
- Index rebuilt before test: yes | no

## Prompt Set

Use the same prompts across retrieval modes when comparing lexical and vector
behavior.

1. `How does provider selection work?`
2. `Why is MCP separate from the backend?`
3. `How are sessions persisted?`
4. `What ADR explains the Mermaid architecture diagram?`
5. `Where does conversation history live?`
6. `What should I check when vector RAG is not working?`

## Single Run Notes

### Prompt

-

### Retrieved Or Cited Chunks

Record the visible cited chunks or the most relevant retrieved sources.

| Rank | Source | Chunk summary | Relevant? | Notes |
| --- | --- | --- | --- | --- |
| 1 |  |  | yes |  |
| 2 |  |  | yes |  |
| 3 |  |  | partial |  |
| 4 |  |  | no |  |

### Answer Quality

- Factual alignment: good | acceptable | weak
- Focus: good | acceptable | weak
- Grounding in cited chunks: good | acceptable | weak
- Hallucinated or unsupported claims:
- Missing important detail:
- Useful synthesis:

### Citation Quality

- Cited the right files: yes | partial | no
- Citations help verify the answer: yes | partial | no
- Low-signal citations:
- Missing expected source:

### Timing And UX

- Total response time:
- Perceived responsiveness: good | acceptable | weak
- UI status was clear: yes | partial | no
- Notes:

### Failure Mode

Use this section only if the result was weak or failed.

- Retrieval issue:
- Answer-generation issue:
- Citation issue:
- Runtime/setup issue:
- Reproducible: yes | no
- Follow-up action:

## Lexical Vs Vector Comparison

Use this table for side-by-side comparison of the same prompt.

| Prompt | Lexical sources | Vector sources | Better retrieval | Better answer | Notes |
| --- | --- | --- | --- | --- | --- |
| How does provider selection work? |  |  | lexical | lexical |  |
| Why is MCP separate from the backend? |  |  | tie | vector |  |
| How are sessions persisted? |  |  | lexical | tie |  |
| What ADR explains the Mermaid architecture diagram? |  |  | lexical | lexical |  |
| Where does conversation history live? |  |  | vector | vector |  |
| What should I check when vector RAG is not working? |  |  | vector | tie |  |

## Scorecard

- Retrieval relevance: good | acceptable | weak
- Citation usefulness: good | acceptable | weak
- Answer grounding: good | acceptable | weak
- Setup clarity: good | acceptable | weak
- Overall result: good | acceptable | weak

## Repeated Findings

Record repeated behavior across multiple prompts or providers.

1.
2.
3.

## Recommended Engineering Follow-Up

- 

## Related References

- [rag-evaluation-guide.md](./rag-evaluation-guide.md)
- [rag-troubleshooting.md](./rag-troubleshooting.md)
- [rag-phase-2-vector-retrieval-design.md](./rag-phase-2-vector-retrieval-design.md)
