# ADR 0011: Use Mermaid As The Architecture Source Of Truth

- Status: `accepted`
- Date: `unknown`

## Context

The project needs an architecture diagram that can evolve with the codebase. A generated SVG was harder to maintain and review as a documentation source of truth.

## Decision

Use Mermaid text diagrams as the maintained source of truth for architecture documentation.

## Rationale

- Mermaid is plain text, which makes it easier to review in diffs and update in Git
- diagram structure stays closer to the surrounding documentation
- the approach fits the repository’s emphasis on inspectable, text-first developer workflows

## Consequences

Positive:

- easier documentation maintenance
- simpler future edits
- architecture diagrams remain version-controlled as text

Negative:

- Mermaid has simpler layout control than hand-tuned SVG
- diagram readability depends more on good labels and surrounding prose

## Primary Implementation

- [architecture-overview.md](../architecture-overview.md)
- [architecture.md](../architecture.md)
- [architecture-walkthrough.md](../architecture-walkthrough.md)

## Revisit Triggers

Reevaluate this decision if:

- Mermaid becomes too limiting for the project’s architecture diagrams
- a future documentation workflow demands richer generated visual assets
