# ADR 0001: Keep MCP As A Separate TypeScript Runtime

- Status: Accepted

## Context

`Local GenAI Lab` has three relevant layers around tool execution:

- the Spring Boot backend for chat orchestration and application APIs
- the MCP server for local tool contracts and tool invocation
- the shell scripts under `scripts/` that perform the operational AWS/report work

The backend is implemented in Java, while the MCP server is implemented in TypeScript and runs as a separate local `stdio` process.

There are two obvious alternatives:

1. move the MCP logic into the Spring backend as Java code
2. rewrite the MCP server in Java but keep it as a separate runtime

The main tradeoff is between:

- language consistency for a Java-first maintainer
- keeping a clear protocol boundary between application orchestration and local tool execution

## Decision

Keep the MCP server:

- in its own `mcp/` directory
- as a separate local runtime boundary
- implemented in TypeScript

The Spring backend remains the orchestrator, but it does not absorb the MCP layer into the web application process.

## Rationale

This decision was taken for these reasons:

- the MCP layer is currently thin protocol glue around a small allowlist of shell-backed tools
- TypeScript is a practical fit for schema-driven tool contracts, JSON payload handling, and shell-wrapper logic
- a separate runtime keeps the MCP boundary explicit instead of letting it drift into “just another Spring service”
- the current implementation already has working contracts, tests, and documentation, so a rewrite would add migration cost without meaningful user-visible benefit

## Consequences

Positive:

- preserves a clean tool-runtime boundary
- keeps MCP language-agnostic from the backend’s point of view
- keeps the MCP layer lightweight and well-suited to contract validation and local process execution
- avoids a rewrite of a working subsystem

Negative:

- the repository keeps two language stacks: Java and TypeScript
- maintainers who are more comfortable in Java need to cross a language boundary to understand MCP internals
- some concepts are represented on both sides of the boundary and must stay aligned through contracts and documentation

## Primary Implementation

- [McpService.java](../../backend/src/main/java/net/jrodolfo/llm/service/McpService.java)
- [mcp/README.md](../../mcp/README.md)
- [start-backend-helper.sh](../../ops/start-backend-helper.sh)

## Revisit Triggers

Reevaluate this decision if one or more of these become true:

- the MCP layer grows substantially beyond thin protocol and shell-wrapper logic
- many more tools require richer shared domain models with the backend
- MCP starts carrying significant orchestration or business logic of its own
- the TypeScript layer becomes a sustained maintenance burden compared with its current size and scope
