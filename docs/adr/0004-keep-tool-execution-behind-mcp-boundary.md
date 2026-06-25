# ADR 0004: Keep Tool Execution Behind The MCP Boundary

- Status: `accepted`
- Date: `unknown`

## Context

The backend needs access to local AWS-oriented tools, but those tools are implemented as shell-backed workflows rather than as Spring-native services.

The project could:

1. invoke shell scripts directly from backend services
2. keep tool execution behind the MCP server boundary

## Decision

Keep tool execution behind the MCP boundary.

The backend calls a local MCP server, and the MCP server remains responsible for:

- validating tool inputs
- invoking the supported shell entrypoints
- normalizing tool outputs

## Rationale

- preserves a clean contract boundary between orchestration and local tool execution
- keeps shell/process concerns out of the main backend orchestration layer
- supports explicit allowlisted tools rather than arbitrary process execution from Spring services
- makes tool contracts visible and testable as their own interface

## Consequences

Positive:

- clearer separation of responsibilities
- safer and more explicit tool invocation model
- better alignment with the repository’s MCP-based architecture

Negative:

- adds one extra runtime boundary
- requires contract alignment between backend expectations and MCP responses

## Revisit Triggers

Reevaluate this decision if:

- the MCP layer becomes trivial enough that the boundary no longer adds value
- the shell-backed tools are replaced with native in-process services
- operational complexity of the separate boundary outweighs its clarity benefits
