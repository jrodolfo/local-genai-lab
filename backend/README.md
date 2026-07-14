# Backend

[![ci](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml)
![license](https://img.shields.io/badge/license-MIT-blue)
![java](https://img.shields.io/badge/java-21+-f89820)
![spring boot](https://img.shields.io/badge/spring%20boot-3.4-6db33f)
![ollama](https://img.shields.io/badge/ollama-default%20provider-222222)
![bedrock](https://img.shields.io/badge/bedrock-remote%20provider%20api-ff9900)
![huggingface](https://img.shields.io/badge/huggingface-remote%20provider%20api-fcc624)
![mcp](https://img.shields.io/badge/mcp-local%20tools-0a7ea4)

Spring Boot API for Agent chat, RAG retrieval, provider/model discovery, local
session persistence, artifact preview, and MCP-backed AWS tooling.

## Run

```bash
mvn spring-boot:run
```

## Tests

Run:

```bash
mvn test
```

The backend test suite is organized into four practical layers:

1. Service and unit-style tests
   Main packages:
   - `src/test/java/net/jrodolfo/llm/service`
   - `src/test/java/net/jrodolfo/llm/config`
   - `src/test/java/net/jrodolfo/llm/health`

   Use these for orchestration, routing, normalization, persistence logic, and focused config or health behavior.

2. Provider and client contract tests
   Main packages:
   - `src/test/java/net/jrodolfo/llm/client`
   - `src/test/java/net/jrodolfo/llm/provider`

   Use these for provider-specific request handling, error classification, caching, and adapter behavior.

3. Focused controller tests
   Main package:
   - `src/test/java/net/jrodolfo/llm/controller`

   Use these for request validation, response shape, controller-specific error handling, and SSE/controller behavior without a full Spring context.

4. Spring Boot smoke and wiring tests
   Main classes:
   - `src/test/java/net/jrodolfo/llm/ApiSmokeIntegrationTest.java`
   - `src/test/java/net/jrodolfo/llm/LlmApplicationTests.java`

   Use these for critical endpoint wiring and full-context smoke validation.

Practical rule:
- if the behavior does not need full application wiring, prefer a narrower layer
- reserve `@SpringBootTest` for startup and high-value endpoint smoke coverage

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
- `GET /api/rag/status`
- `POST /api/rag/index`
- `POST /api/rag/query`
- `POST /api/rag/compare`

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

- default local runtime: `ollama`
- optional remote provider API: `bedrock`
- optional remote provider API: `huggingface`

Relevant settings:

- `APP_MODEL_PROVIDER` default: `ollama`
- `OLLAMA_BASE_URL` default: `http://localhost:11434`
- `OLLAMA_DEFAULT_MODEL` default: `llama3:8b`
- `BEDROCK_REGION` default: `us-east-2`
- `BEDROCK_MODEL_ID` default: empty
- `HUGGINGFACE_BASE_URL` default: `https://router.huggingface.co/v1/chat/completions`
- `HUGGINGFACE_DEFAULT_MODEL` default: empty
- `HUGGINGFACE_MODELS` default: empty

Bedrock supports both normal chat and streaming chat.
Hugging Face uses a configured candidate list and validates which candidates
are usable through the remote provider API before returning model options to the
frontend.

For local provider switching without memorizing JVM flags, use the unified helper script documented in [../docs/providers.md](../docs/providers.md):

- [`../ops/start-backend-helper.sh`](../ops/start-backend-helper.sh)

That script uses `APP_MODEL_PROVIDER` to choose the backend default provider. The chat API can still override provider per request at runtime.
The frontend selector only shows providers that are configured in the running backend process.

`GET /api/models` exposes provider-aware model options for the UI:

- `GET /api/models?provider=ollama`
- `GET /api/models?provider=bedrock`
- `GET /api/models?provider=huggingface`
- if `provider` is omitted, the backend returns models for the configured default provider
- `ollama`: returns installed local models from Ollama plus the configured default model
- `bedrock`: returns discovered Bedrock inference profiles when available and falls back to the configured model id if discovery fails
- `huggingface`: validates the configured candidate list and returns only the models that are currently usable, with the default falling back to the first usable model when needed

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

Relevant RAG settings:

- `RAG_ENABLED` default: `true`
- `RAG_CORPUS_ROOT` default: `docs`
- `RAG_MAX_CHUNK_SIZE` default: `900`
- `RAG_CHUNK_OVERLAP` default: `160`
- `RAG_TOP_K` default: `4`
- `RAG_RETRIEVAL_MODE` default: `lexical`
- `RAG_VECTOR_STORE` default: `in-memory`
- `RAG_QDRANT_URL` default: `http://localhost:6333`
- `RAG_QDRANT_COLLECTION` default: `local_genai_lab_docs`
- `RAG_EMBEDDING_PROVIDER` default: `ollama`
- `RAG_EMBEDDING_MODEL` default: `nomic-embed-text`

Lexical retrieval remains the default. When `RAG_RETRIEVAL_MODE=vector`, the
backend embeds the same docs corpus with the configured embedding provider/model
and uses `RAG_VECTOR_STORE` to choose `in-memory` or `qdrant`.

The backend resolves the MCP working directory from the repository root so it remains stable even when the JVM starts from `backend/`.

MCP-backed AWS tools run in the backend runtime. In host-run mode, they use the
host `aws`, `jq`, and AWS credential configuration. In Docker mode, the backend
image includes AWS CLI and `jq`, but host AWS credentials are not mounted by
default. Copy `.env.docker-aws-tools.example` to `.env.docker-aws-tools` to let
the Docker lifecycle scripts mount your local AWS configuration read-only.

## RAG API

`GET /api/rag/status` returns RAG readiness, corpus/index status, default
retrieval configuration, embedding configuration, and Qdrant reachability when
Qdrant is relevant.

`POST /api/rag/index` rebuilds the selected/default RAG index. Lexical rebuilds
the in-memory keyword index. Vector rebuilds embed chunks with the configured
embedding provider/model and writes them to the selected vector store.

`POST /api/rag/query` asks one RAG question, uses one retrieval target, and saves
the answer to a RAG session.

```json
{
  "question": "How are sessions persisted?",
  "provider": "ollama",
  "model": "llama3:8b",
  "sessionId": "optional-existing-rag-session-id",
  "retrievalTarget": "lexical"
}
```

Supported `retrievalTarget` values:

- `lexical`
- `vector:in-memory`
- `vector:qdrant`

`POST /api/rag/compare` runs the same question across multiple retrieval targets
and does not save the generated answers to a RAG session. Use it for retrieval
evaluation, not normal conversation history.

```json
{
  "question": "How are sessions persisted?",
  "provider": "ollama",
  "model": "llama3:8b",
  "retrievalTargets": ["lexical", "vector:in-memory", "vector:qdrant"]
}
```

If `retrievalTargets` is omitted, the backend compares all supported targets.
The response contains one result per target. A target can fail, for example
because Qdrant is unavailable, while the overall comparison request still
returns the other target results.

## Sessions and Storage

Sessions are stored locally as JSON files.

- `APP_STORAGE_SESSIONS_DIRECTORY` default: `data/sessions`
- `APP_STORAGE_REPORTS_DIRECTORY` default: `agents/reports`

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
