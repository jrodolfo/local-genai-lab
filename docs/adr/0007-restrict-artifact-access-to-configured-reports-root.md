# ADR 0007: Restrict Artifact Access To The Configured Reports Root

- Status: `accepted`
- Date: `unknown`

## Context

The backend exposes artifact listing and preview behavior for generated reports. Without a strict boundary, artifact APIs could drift into arbitrary filesystem reads.

## Decision

Restrict artifact access to paths under the configured reports root and expose read-only behavior only.

## Rationale

- report artifacts are part of the product, but arbitrary filesystem access is not
- path validation is a meaningful safety boundary even in a local-first project
- this keeps artifact APIs aligned with the tool/report model rather than turning them into a generic file browser

## Consequences

Positive:

- clearer security boundary
- safer artifact preview implementation
- easier reasoning about what the backend is allowed to expose

Negative:

- artifact APIs are intentionally narrower than a generic file access layer
- report locations must remain aligned with the configured reports directory

## Revisit Triggers

Reevaluate this decision if:

- the project later needs a broader storage abstraction
- report artifacts move to a different storage model with its own access controls
