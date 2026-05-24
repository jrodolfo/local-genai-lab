# Architecture

This document gives the system-level view of `Local GenAI Lab`: the main runtime components, the core request flows, and the design decisions that shape the repository.

## System Overview

`Local GenAI Lab` is a local-first GenAI application with four main runtime layers:

- a React frontend for chat, session management, model/provider selection, and artifact inspection
- a Spring Boot backend for orchestration, provider selection, prompt construction, streaming, persistence, and API exposure
- an MCP server that exposes local AWS-oriented tools with typed contracts
- shell scripts and AWS CLI commands that generate reports and artifacts consumed by the backend and UI

At runtime, the backend is the central coordinator. The frontend never talks directly to Ollama, Bedrock, or the local tools.

## Core Diagram

See the maintained Mermaid version in [architecture-overview.md](./architecture-overview.md).

## Architecture Decisions

Accepted architecture decisions live under [`docs/adr/`](./adr/).

Start with the folder guide in [docs/adr/README.md](./adr/README.md) and use
[docs/adr/template.md](./adr/template.md) for new records.

Current ADRs:

- [ADR 0001: Keep MCP As A Separate TypeScript Runtime](./adr/0001-mcp-separate-typescript-runtime.md)
- [ADR 0002: Use Runtime Provider Switching Per Request](./adr/0002-runtime-provider-switching-per-request.md)
- [ADR 0003: Show Only Configured Providers In The Selector](./adr/0003-show-only-configured-providers-in-selector.md)
- [ADR 0004: Keep Tool Execution Behind The MCP Boundary](./adr/0004-keep-tool-execution-behind-mcp-boundary.md)
- [ADR 0005: Ground Tool Results Through The Selected Model](./adr/0005-ground-tool-results-through-the-selected-model.md)
- [ADR 0006: Persist Sessions As Local JSON Files](./adr/0006-persist-sessions-as-local-json-files.md)
- [ADR 0007: Restrict Artifact Access To The Configured Reports Root](./adr/0007-restrict-artifact-access-to-configured-reports-root.md)
- [ADR 0008: Use Curated Hugging Face Candidates Instead Of Full Catalog Browsing](./adr/0008-use-curated-hugging-face-candidates-not-full-catalog.md)
- [ADR 0009: Use Short-Lived Provider Status Caching](./adr/0009-use-short-lived-provider-status-caching.md)
- [ADR 0010: Use A Unified Backend Startup Script](./adr/0010-use-unified-backend-startup-script.md)
- [ADR 0011: Use Mermaid As The Architecture Source Of Truth](./adr/0011-use-mermaid-as-architecture-source-of-truth.md)

## Main Components

### Frontend

The frontend is responsible for:

- rendering the chat experience
- selecting provider and model at runtime
- showing per-turn provider/model provenance
- listing, loading, deleting, importing, and exporting sessions
- rendering structured tool results and artifact previews

Key boundary:

- it sends requests only to the backend
- it treats the backend as the source of truth for available providers, available models, sessions, and artifact access

### Backend API

The Spring Boot backend exposes:

- chat endpoints
- streaming SSE chat
- session APIs
- model discovery APIs
- artifact preview/listing APIs
- Actuator health/info
- OpenAPI/Swagger for the application API

It is the system’s orchestration layer and the only runtime that knows how to combine chat, tools, persistence, and providers.

### Chat Orchestration Layer

The orchestration layer is responsible for:

- routing plain chat vs tool-assisted chat
- selecting the provider per request
- choosing the appropriate prompt shape for the selected provider
- handling streaming and non-streaming flows
- storing session turns and assistant metadata
- coordinating pending clarifications when tool inputs are incomplete

This is the architectural center of the codebase.

### Provider Layer

The provider layer currently supports three backends:

- Ollama
- Amazon Bedrock
- Hugging Face

Provider selection is runtime-selectable per request. The backend maintains a Provider Registry rather than a single startup-only provider.

Important distinction:

- Ollama plain chat uses role-based chat messages
- Bedrock now preserves structured system, user, and assistant messages when that context is available
- Hugging Face starts from a configured candidate list and validates the usable subset through its hosted chat endpoint

### MCP Server

The MCP server exposes local tools with typed input/output contracts. These tools sit between the backend and the shell scripts.

Current responsibilities:

- validate tool inputs
- execute supported local report flows
- normalize and validate tool outputs
- return structured tool results or structured MCP/runtime errors

### Shell Scripts

The shell layer performs the operational work, such as:

- AWS region audit collection
- S3 CloudWatch metric collection
- report file generation under the configured reports root

These scripts are implementation detail behind the MCP boundary rather than direct frontend/backend APIs.

### Session Storage

Sessions are stored as local JSON files. Stored data includes:

- conversation turns
- per-turn provider/model metadata
- tool usage metadata
- pending clarification state
- generated title/summary metadata

### Artifacts and Reports

Generated reports and related files live under the configured reports directory. The backend exposes read-only listing and preview behavior relative to that configured root.

This allows:

- structured tool cards in the UI
- safe preview of report outputs
- exportable artifacts without exposing arbitrary filesystem reads

## Request Flows

### Plain Chat

```text
React -> /api/chat or /api/chat/stream
      -> backend provider selection
      -> prompt construction
      -> Ollama, Bedrock, or Hugging Face
      -> assistant response
      -> session persistence
      -> UI render
```

Step-by-step:

1. the user selects provider/model in the UI
2. the frontend sends the message to the backend
3. the backend resolves the selected provider from the Provider Registry
4. the backend builds the appropriate prompt/message structure
5. the provider generates the response
6. the backend stores the assistant turn and returns it to the UI

### Tool-Assisted Chat

```text
React
  -> Spring Boot backend
    -> tool decision
      -> MCP tool
        -> shell script
          -> AWS CLI / reports
    -> prompt enrichment with structured tool result
    -> Ollama, Bedrock, or Hugging Face
    -> assistant response + structured tool result
```

Step-by-step:

1. the backend receives the user message
2. the tool decision layer decides whether a tool is needed
3. if required, the backend invokes the MCP tool
4. the MCP tool executes the shell-backed workflow
5. the backend receives structured tool output and artifact references
6. the backend enriches the prompt with that tool context
7. the selected provider generates the final answer
8. the UI renders both the assistant answer and the structured tool result

Important design point:

- successful tool execution does not bypass the model
- tool output grounds the prompt, but the final wording still depends on the selected model

### Streaming Chat

```text
React -> /api/chat/stream
      -> backend stream orchestration
      -> tool phase events when applicable
      -> provider streaming path
      -> SSE start / delta / complete events
      -> session persistence on successful completion
```

The backend explicitly manages stream lifecycle, disconnect handling, and cancellation to avoid persisting aborted assistant turns as completed responses.

For tool-assisted streaming turns, the backend also emits explicit tool-phase events so the frontend can show in-flight lifecycle states such as tool decision, tool execution, and transition back to answer generation without guessing from the prompt text.

### Session Load / Export / Import

```text
React -> session API
      -> JSON-backed session storage
      -> restored conversation state / exported file / imported session
```

The UI sidebar is backed by persisted session metadata rather than ephemeral in-memory state.

## Provider Architecture

The provider subsystem is built around:

- a configured default provider
- a Provider Registry over the supported providers
- per-request provider resolution
- provider-aware model discovery

### Ollama

The backend discovers locally installed Ollama models and exposes them to the UI. Plain chat uses Ollama’s chat-style API for better compatibility with normal conversational turns.

### Bedrock

The backend discovers Bedrock inference profiles when available and falls back to the configured model id when discovery fails. Bedrock still depends on valid AWS credentials and a region/model combination your account can invoke.

### Runtime Switching

The frontend can switch between providers without restarting the backend. The backend still needs both provider environments to be usable:

- Ollama reachable when Ollama is selected
- valid AWS/Bedrock configuration when Bedrock is selected
- valid Hugging Face token/base URL/default model when Hugging Face is selected

Important selector rule:

- the UI only shows providers configured in the running backend process
- helper scripts can auto-load a repo-local `.env` so one backend process can expose multiple providers cleanly

Provider status is a separate, lightweight troubleshooting surface:

- the backend caches provider status briefly to avoid excessive live discovery checks
- the UI shows `Last checked` from that backend status response
- the UI can manually re-fetch status with `Refresh status` without reloading the provider model list

Completed tool-assisted replies expose two complementary UI layers:

- compact tool provenance in the assistant message
- artifact inspection through the artifact inspector panel for previews and file lists

## Tool Orchestration Architecture

The tool path has four distinct stages:

1. tool decision
2. optional pending clarification
3. MCP execution
4. prompt enrichment and final model answer

The tool layer is not a separate agent framework. It is backend-driven orchestration around a small set of well-defined local tools.

### Pending Clarification

If a tool request is missing required inputs, the backend stores pending state and asks a follow-up question instead of forcing the LLM to guess missing parameters.

### MCP Contracts

The MCP tools expose typed schemas for:

- input payloads
- success payloads
- runtime error payloads

This creates a stable contract between backend orchestration and local tool execution.

## Data and Storage

### Session Files

Session files persist:

- user turns
- assistant turns
- provider/model metadata
- tool metadata
- pending tool state

This makes mixed-provider sessions understandable after reload.

### Reports Root

Artifact access is bounded to the configured reports directory. The directory may be inside or outside the repository root, but artifact APIs use paths relative to that configured root rather than arbitrary absolute paths.

## Health and Diagnostics

Two layers of diagnostics exist:

### Application Health

Spring Actuator reports backend component health, including:

- model provider readiness
- storage readiness
- MCP configuration readiness

MCP health is intentionally config-oriented and cheap. It does not perform expensive tool handshakes during normal health checks.

### End-to-End Local Checks

The script `scripts/check-app.sh` gives a broader local-stack view by checking:

- backend reachability
- frontend reachability
- Ollama reachability

This is intentionally different from Actuator health. One is backend component health; the other is local runtime sanity.

## Key Design Decisions

- backend-driven orchestration instead of direct frontend-to-model calls
- local-first workflow over multi-tenant production architecture
- runtime provider switching for experimentation
- MCP as the tool boundary instead of direct shell execution from the backend
- typed MCP contracts for stable tool integration
- preserved model variance after tool grounding rather than replacing responses with deterministic templates

## Current Limitations

- the project is designed for single-user local use, not shared multi-tenant deployment
- model behavior still varies significantly across providers and models
- Bedrock depends on local AWS credentials and account-specific model access
- shell-backed tools assume a local developer environment with AWS CLI and related dependencies installed

## Related Documents

- [Main README](../README.md)
- [Provider Switching](./providers.md)
- [Backend README](../backend/README.md)
- [MCP Tool Contracts](../mcp/TOOL_CONTRACTS.md)
