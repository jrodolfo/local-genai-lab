# ADR 0014: Compare RAG Retrieval Targets Without Session Side Effects

- Status: `accepted`
- Date: `2026-06-16`

## Context

The RAG workspace supports multiple retrieval targets over the same local docs
corpus:

- `lexical`
- `vector:in-memory`
- `vector:qdrant`

This is a lab project, so comparing retrieval behavior is part of the product
value. A developer should be able to ask the same question against multiple
targets and inspect differences in answers, citations, timing, and failures.

The previous UI-focused comparison work was reverted because it overlapped with
layout instability. The useful architectural idea remains valid, but it should
be recovered backend-first so comparison behavior can be tested independently of
React layout.

## Decision

Add a backend RAG comparison endpoint that runs one question across multiple
retrieval targets and returns one structured result per target.

The comparison flow must:

- default to comparing all supported retrieval targets when none are provided
- accept an explicit target list for narrower comparisons
- return successful answers with sources, retrieval metadata, provider metadata,
  and timing metadata
- return per-target errors instead of failing the entire comparison when one
  target is unavailable
- avoid saving comparison answers to RAG sessions

The normal `/api/rag/query` flow remains the persisted single-answer path. The
comparison flow is evaluation-oriented and must not change session history unless
a future ADR explicitly decides otherwise.

## Rationale

Backend-first comparison protects the clean RAG service boundary and avoids
reintroducing risky UI layout changes.

Returning partial results is important because `vector:qdrant` has operational
dependencies that `lexical` and `vector:in-memory` do not. If Qdrant is not
running or the collection is missing, the user should still be able to compare
the available local targets and see the Qdrant failure clearly.

Keeping comparison out of session persistence also keeps saved RAG sessions
easy to understand. A saved RAG turn represents one user question and one chosen
retrieval target, not a diagnostic matrix.

## Consequences

Positive:

- comparison behavior can be tested before any UI is added
- Qdrant failures are isolated to the Qdrant result
- normal RAG session history stays clean
- future UI work can render a stable response contract instead of inventing
  comparison behavior in React

Negative:

- comparison requests may call the selected model multiple times
- comparison can be slower than a normal RAG query
- users may need clear UI copy later so they understand that comparison results
  are diagnostic and not saved

## Revisit Triggers

Revisit this decision if:

- comparison results need to be saved as first-class RAG session artifacts
- the UI needs side-by-side comparison history
- model cost or latency makes multi-target comparison too expensive by default
- more retrieval targets are added and comparison needs target grouping,
  sampling, or limits
