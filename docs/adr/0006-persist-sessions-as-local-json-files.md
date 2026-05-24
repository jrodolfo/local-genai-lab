# ADR 0006: Persist Sessions As Local JSON Files

- Status: Accepted

## Context

The project needs conversation persistence for:

- reopening prior sessions
- exporting and importing sessions
- preserving provider/model provenance across turns

Possible persistence options included:

1. a database
2. in-memory only sessions
3. local JSON-backed session files

## Decision

Persist sessions as local JSON files.

## Rationale

- the project is local-first and single-user in scope
- JSON files are simple to inspect, debug, export, and version mentally
- the storage model aligns with the educational and developer-machine focus of the repo
- a database would add operational complexity that does not currently buy much value

## Consequences

Positive:

- easy local inspection and portability
- straightforward export/import model
- low operational overhead

Negative:

- not designed for multi-user concurrency
- not intended for high-scale production persistence requirements
- file-based storage requires careful path handling and validation

## Primary Implementation

- [FileChatSessionStore.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/service/FileChatSessionStore.java)
- [ChatSessionService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/service/ChatSessionService.java)
- [ChatSessionImportService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/service/ChatSessionImportService.java)
- [ChatSessionExportService.java](/Users/jrodolfo/workspace/aws/local-genai-lab/backend/src/main/java/net/jrodolfo/llm/service/ChatSessionExportService.java)
- [sessionApi.js](/Users/jrodolfo/workspace/aws/local-genai-lab/frontend/src/api/sessionApi.js)

## Revisit Triggers

Reevaluate this decision if:

- the project moves beyond single-user local scope
- session volume or concurrency makes file-based storage awkward
- richer querying requirements exceed the current session indexing approach
