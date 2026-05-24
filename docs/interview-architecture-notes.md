# Interview Architecture Notes

This document is a quick interview-oriented guide to `Local GenAI Lab`.

It is intentionally shorter and more conversational than
[`architecture.md`](./architecture.md) and the ADR set in [`adr/`](./adr/).

## One-Minute System Summary

This repository is a local-first GenAI lab with four main runtime layers:

- a React frontend for chat, provider/model selection, sessions, and artifact inspection
- a Spring Boot backend for orchestration, provider routing, persistence, and APIs
- a local MCP server for typed tool execution
- shell scripts and AWS CLI commands that generate report artifacts

The main architectural idea is that the backend is the coordinator. The UI
never talks directly to LLM providers or local tools. Tool-assisted results are
grounded through the selected model rather than bypassing it.

## Core Design Story

If I had to explain the project in a few sentences:

1. the frontend sends a chat request to the Spring Boot backend
2. the backend selects the provider for that request from the Provider Registry
3. the backend decides whether the request needs a tool
4. if needed, it calls the MCP server, which invokes shell-backed workflows
5. the backend enriches the prompt with the structured tool result
6. Ollama, Bedrock, or Hugging Face generates the final answer
7. the backend stores the turn in a local JSON session and returns the response

This design was chosen so the project can demonstrate more than plain chat. It
can show provider abstraction, tool-assisted orchestration, persistent session
memory, artifact inspection, and operational tradeoffs in one repo.

## Main Architectural Decisions

### 1. Keep the backend as the central orchestrator

Why:

- it gives one place to coordinate chat, tools, providers, persistence, and artifact access
- it keeps the frontend thin and provider-agnostic
- it avoids direct browser access to local tools or credentials

Related ADRs:

- [ADR 0002](./adr/0002-runtime-provider-switching-per-request.md)
- [ADR 0005](./adr/0005-ground-tool-results-through-the-selected-model.md)

### 2. Keep MCP as a separate TypeScript runtime

Why:

- it preserves a clear runtime boundary between application orchestration and local tool execution
- TypeScript is a pragmatic fit for thin MCP contracts and shell-wrapper logic
- it reduces the temptation to collapse tools into “just another Spring service”

Tradeoff:

- the repo keeps both Java and TypeScript

Related ADRs:

- [ADR 0001](./adr/0001-mcp-separate-typescript-runtime.md)
- [ADR 0004](./adr/0004-keep-tool-execution-behind-mcp-boundary.md)

### 3. Select the provider per request, not only at startup

Why:

- it lets one backend process expose multiple configured providers
- it makes provider comparison part of the product experience
- it keeps the frontend flexible without requiring backend restarts

Tradeoff:

- the backend must make provider availability explicit and safe at runtime

Related ADR:

- [ADR 0002](./adr/0002-runtime-provider-switching-per-request.md)

### 4. Show only configured providers in the selector

Why:

- “supported in code” is not the same as “usable in this running backend process”
- hiding unconfigured providers avoids misleading the user
- it keeps runtime switching honest

Related ADR:

- [ADR 0003](./adr/0003-show-only-configured-providers-in-selector.md)

### 5. Use curated Hugging Face candidates instead of full catalog browsing

Why:

- the Hugging Face public catalog is too broad and inconsistent for a simple model selector
- not every public model is usable through the same hosted chat endpoint or token
- a validated configured subset is a better fit for this project’s scope

Tradeoff:

- the dropdown is intentionally narrower than the full Hugging Face universe

Related ADR:

- [ADR 0008](./adr/0008-use-curated-hugging-face-candidates-not-full-catalog.md)

### 6. Persist sessions as local JSON files

Why:

- this is a local-first educational project, not a multi-tenant platform
- local JSON sessions make export/import, inspection, and debugging straightforward
- it keeps infrastructure requirements low

Tradeoff:

- no multi-user persistence model
- schema evolution requires explicit compatibility care

Related ADR:

- [ADR 0006](./adr/0006-persist-sessions-as-local-json-files.md)

### 7. Restrict artifact access to the configured reports root

Why:

- tool results often reference local files
- the UI needs read-only access to summaries, reports, and file lists
- bounding access to a configured root avoids arbitrary filesystem reads

Related ADR:

- [ADR 0007](./adr/0007-restrict-artifact-access-to-configured-reports-root.md)

## What I Would Emphasize In An Interview

### Clear runtime boundaries

- frontend owns interaction and presentation
- backend owns orchestration and persistence
- MCP owns typed tool execution
- shell scripts and AWS CLI do operational work behind MCP

### Local-first design with deliberate limits

This project is intentionally optimized for learning, experimentation, and AWS
GenAI exam preparation. That is why it uses local JSON sessions, local artifact
inspection, and a single-process backend rather than a database-backed or
multi-tenant architecture.

### Tool-assisted chat still goes through the model

The backend does not treat tool output as the final answer. It treats tool
output as grounding context for the selected model. That preserves provider
comparison and keeps the system’s behavior aligned with LLM-assisted reasoning.

### Configuration-aware UX

Provider switching is runtime-flexible, but the UI only exposes providers that
are actually configured in the current backend process. That is a product
decision as much as a technical one.

### Defensive handling of operational ambiguity

The project separates:

- provider availability from provider status
- successful tool results from clarification-needed tool turns
- stored session replay from live in-memory UI state

That makes the system easier to reason about during failures and restores.

## Likely Interview Questions

### Why not call Ollama, Bedrock, or Hugging Face directly from the frontend?

Current answer:

- the backend needs to coordinate provider selection, tool orchestration, prompt enrichment, persistence, and artifact access
- direct browser calls would fragment that logic and complicate credentials and local-tool boundaries

### Why is MCP separate from the backend?

Current answer:

- MCP is a protocol and runtime boundary, not just a Java package
- it keeps local tool execution isolated from the web application
- TypeScript is a practical fit for thin tool contracts and shell-wrapper logic

Related ADRs:

- [ADR 0001](./adr/0001-mcp-separate-typescript-runtime.md)
- [ADR 0004](./adr/0004-keep-tool-execution-behind-mcp-boundary.md)

### Why use TypeScript for MCP if the backend is Java?

Current answer:

- because the MCP layer is mostly lightweight protocol glue and schema validation
- TypeScript is efficient for that job
- Java would be more uniform for the maintainer, but not clearly better for this thin layer yet

### Why not use a database for sessions?

Current answer:

- the project is local-first and educational
- local JSON keeps setup simple and makes export/import and manual inspection easy
- a database would add infrastructure without current product value

Related ADR:

- [ADR 0006](./adr/0006-persist-sessions-as-local-json-files.md)

### Why not list all Hugging Face models dynamically?

Current answer:

- the full public catalog is too broad and inconsistent for a safe dropdown
- model usability depends on endpoint, account, and token constraints
- configured candidate validation is a more controlled design for this repo

Related ADR:

- [ADR 0008](./adr/0008-use-curated-hugging-face-candidates-not-full-catalog.md)

### Why does the UI only show configured providers?

Current answer:

- because showing every compiled-in provider would imply usability that may not exist in the running process
- the selector reflects actual backend availability, not theoretical support

Related ADR:

- [ADR 0003](./adr/0003-show-only-configured-providers-in-selector.md)

### Why use Mermaid as the architecture source of truth?

Current answer:

- Mermaid diagrams are easier to maintain in Git, review in diffs, and update as the system changes
- it keeps the diagram close to the documentation and easier to evolve than generated image assets

Related ADR:

- [ADR 0011](./adr/0011-use-mermaid-as-architecture-source-of-truth.md)

## What I Would Improve Next

If asked how I would evolve the system, I would mention:

- stronger real-world smoke coverage across provider/tool combinations
- clearer restored-session handling when referenced artifacts are missing on disk
- richer provider-specific error explanations where they improve UX without adding noise
- reevaluating the MCP implementation language only if the MCP layer grows far beyond thin protocol glue

## Where To Read More

- System overview: [`architecture.md`](./architecture.md)
- Architecture diagram: [`architecture-overview.md`](./architecture-overview.md)
- Architectural decisions: [`adr/README.md`](./adr/README.md)
