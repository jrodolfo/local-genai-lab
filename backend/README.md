# Backend

[![ci](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml)
![license](https://img.shields.io/badge/license-MIT-blue)
![java](https://img.shields.io/badge/java-21+-f89820)
![spring boot](https://img.shields.io/badge/spring%20boot-3.4-6db33f)
![ollama](https://img.shields.io/badge/ollama-default%20provider-222222)
![bedrock](https://img.shields.io/badge/bedrock-optional%20provider-ff9900)
![mcp](https://img.shields.io/badge/mcp-local%20tools-0a7ea4)

Spring Boot API for chat, local sessions, artifact preview, and MCP-backed AWS tooling.

## Run

```bash
mvn spring-boot:run
```

## API Docs and Operations

- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health: `http://localhost:8080/actuator/health`
- Info: `http://localhost:8080/actuator/info`

`GET /actuator` redirects to `/actuator/health`. Swagger excludes `/actuator/**` so the generated docs stay focused on the application API.

## Main Endpoints

- `POST /api/chat`
- `POST /api/chat/stream`
- `GET /api/models`
- `GET /api/sessions`
- `GET /api/sessions/{sessionId}`
- `GET /api/sessions/{sessionId}/export`
- `POST /api/sessions/import`
- `DELETE /api/sessions/{sessionId}`
- `GET /api/artifacts/files`
- `GET /api/artifacts/preview`
- `GET /api/tools`
- `POST /api/tools/aws-region-audit`
- `POST /api/tools/s3-cloudwatch-report`
- `GET /api/tools/reports`
- `POST /api/tools/reports/read`

`/api/chat` and `/api/chat/stream` accept:

```json
{
  "message": "Explain recursion",
  "provider": "ollama",
  "model": "llama3:8b",
  "sessionId": "optional-existing-session-id"
}
```

## Providers

The backend uses a model-provider abstraction.

- default provider: `ollama`
- optional provider: `bedrock`

Relevant settings:

- `APP_MODEL_PROVIDER` default: `ollama`
- `OLLAMA_BASE_URL` default: `http://localhost:11434`
- `OLLAMA_DEFAULT_MODEL` default: `llama3:8b`
- `BEDROCK_REGION` default: `us-east-1`
- `BEDROCK_MODEL_ID` default: empty

Bedrock supports both normal chat and streaming chat.

For local provider switching without memorizing JVM flags, use the helper scripts documented in [../docs/providers.md](../docs/providers.md):

- [`../scripts/run-backend-ollama.sh`](../scripts/run-backend-ollama.sh)
- [`../scripts/run-backend-bedrock.sh`](../scripts/run-backend-bedrock.sh)

Those scripts set the backend default provider. The chat API can still override provider per request at runtime.

`GET /api/models` exposes provider-aware model options for the UI:

- `GET /api/models?provider=ollama`
- `GET /api/models?provider=bedrock`
- if `provider` is omitted, the backend returns models for the configured default provider
- `ollama`: returns installed local models from Ollama plus the configured default model
- `bedrock`: returns discovered Bedrock inference profiles when available and falls back to the configured model id if discovery fails

When a tool call succeeds, the backend still sends the enriched prompt to the selected model. It does not bypass the model or replace the final wording with a deterministic backend template by default. That means different models can still produce noticeably different final answers even when they receive the same grounded tool context.

## MCP Integration

The backend can launch the local MCP server in [`../mcp`](../mcp) as a subprocess.

Build the MCP server first:

```bash
cd ../mcp
npm install
npm run build
```

Then start the backend normally:

```bash
cd ../backend
mvn spring-boot:run
```

MCP is enabled by default. Disable it only when needed:

```bash
cd ../backend
MCP_ENABLED=false mvn spring-boot:run
```

Relevant MCP settings:

- `MCP_ENABLED` default: `true`
- `MCP_COMMAND` default: `node`
- `MCP_WORKING_DIRECTORY` default: `mcp`
- `MCP_ARG_1` default: `dist/index.js`
- `MCP_STARTUP_TIMEOUT_SECONDS` default: `10`
- `MCP_TOOL_TIMEOUT_SECONDS` default: `120`

The backend resolves the MCP working directory from the repository root so it remains stable even when the JVM starts from `backend/`.

## Sessions and Storage

Sessions are stored locally as JSON files.

- `APP_STORAGE_SESSIONS_DIRECTORY` default: `data/sessions`
- `APP_STORAGE_REPORTS_DIRECTORY` default: `scripts/reports`

Those defaults are resolved from the repository root, so the backend reports the correct local paths even when started from different working directories.
`APP_STORAGE_REPORTS_DIRECTORY` may also be set to an absolute path outside the repository.

Session support includes:

- local JSON-backed conversation memory
- generated titles and summaries
- text search with `query=...`
- additional filters for `provider`, `toolUsage`, and `pending`
- JSON and Markdown export
- JSON import with collision-safe session ids
- backend-managed opaque session ids with strict validation

## Tooling and Artifacts

The backend supports:

- LLM-assisted tool routing with rule-based fallback
- multi-turn clarification for missing tool inputs
- structured `toolResult` payloads for supported report flows
- read-only artifact preview under the configured reports directory

Artifact path contract:

- the configured reports directory can be repo-local or an absolute external directory
- `GET /api/artifacts/files` and `GET /api/artifacts/preview` accept only paths relative to that configured reports directory
- absolute request paths are rejected intentionally to keep artifact reads bounded to the configured root

Set `APP_TOOLS_LOG_PLANNER=true` to log raw planner output, parsed decisions, and fallback usage during local tuning.

## Actuator Notes

`/actuator/health` includes app-specific checks for:

- active model provider readiness
- active provider configuration, reachability, and readiness
- MCP configuration and cheap runnable-state checks only
- local sessions and reports directories

`/actuator/info` includes backend/runtime details such as:

- active provider and model
- MCP enablement and resolved working directory
- resolved storage paths

## Scope and Limits

- this backend is intended for a single-user local lab, not a shared production deployment
- health/readiness focuses on backend-local dependencies and cheap checks, not full end-to-end environment validation
- MCP execution favors simple local subprocess orchestration over long-lived process pooling
- artifact access is intentionally read-only and constrained to the configured reports root
- local AWS credentials, Ollama state, and developer-machine filesystem layout remain part of the runtime contract

## Notes

- normal chat responses can include provider metadata
- streamed replies use typed JSON SSE events: `start`, `delta`, and `complete`
- reopened sessions can preserve tool metadata, provider metadata, and structured tool results
- artifact preview access is read-only and constrained to the configured reports directory
