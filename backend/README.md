# Backend

[![ci](https://github.com/jrodolfo/llm-pet-project/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jrodolfo/llm-pet-project/actions/workflows/ci.yml)
![java](https://img.shields.io/badge/java-21+-f89820)
![spring boot](https://img.shields.io/badge/spring%20boot-3.4-6db33f)
![ollama](https://img.shields.io/badge/ollama-default%20provider-222222)
![bedrock](https://img.shields.io/badge/bedrock-optional%20provider-ff9900)
![mcp](https://img.shields.io/badge/mcp-optional%20integration-0a7ea4)

Spring Boot API that routes chat through a model-provider abstraction, supports Ollama and Amazon Bedrock, orchestrates optional local MCP tools, and persists local JSON chat sessions.

## Run

```bash
mvn spring-boot:run
```

## Endpoints

- `POST /api/chat`
- `POST /api/chat/stream` (SSE)
- `GET /api/sessions`
- `GET /api/sessions/{sessionId}`
- `DELETE /api/sessions/{sessionId}`
- `GET /api/tools`
- `POST /api/tools/aws-region-audit`
- `POST /api/tools/s3-cloudwatch-report`
- `GET /api/tools/reports`
- `POST /api/tools/reports/read`

`/api/chat` and `/api/chat/stream` accept:

```json
{
  "message": "Explain recursion",
  "model": "llama3:8b",
  "sessionId": "optional-existing-session-id"
}
```

The backend returns the active `sessionId` in chat responses so the frontend can continue the same local conversation.
Normal chat responses also include optional provider metadata. For Bedrock, that metadata includes stop reason, token counts, total duration, and Bedrock latency when the runtime returns them.
Streaming chat emits `metadata` SSE events at the start of the stream and can emit a final `metadata` event with provider details before `[DONE]`.

## Local MCP Integration

The backend can invoke the local MCP server in [`../mcp`](../mcp) as a separate process.

Build the MCP server first:

```bash
cd ../mcp
npm install
npm run build
```

Then enable MCP when starting Spring Boot:

```bash
cd ../backend
MCP_ENABLED=true mvn spring-boot:run
```

Relevant environment variables:

- `APP_MODEL_PROVIDER` default: `ollama`
- `OLLAMA_DEFAULT_MODEL` default: `llama3:8b`
- `BEDROCK_REGION` default: `us-east-1`
- `BEDROCK_MODEL_ID` default: empty
- `APP_TOOLS_ROUTING_MODE` default: `hybrid`
- `APP_TOOLS_LOG_PLANNER` default: `false`
- `MCP_ENABLED`
- `MCP_COMMAND` default: `node`
- `MCP_WORKING_DIRECTORY` default: `../mcp`
- `MCP_ARG_1` default: `dist/index.js`
- `MCP_STARTUP_TIMEOUT_SECONDS`
- `MCP_TOOL_TIMEOUT_SECONDS`

## Local Conversation Memory

Chat sessions are stored locally as JSON files under `../data/sessions` by default.

The backend exposes a small local session API so the frontend can list, reopen, and delete stored chats.
It also keeps pending tool clarification state in the session so a short follow-up reply can complete a previously blocked tool request.
That pending state can also be surfaced in the chat UI as an informational hint.
Session files also store generated local `title` and `summary` metadata for easier sidebar browsing.
Tool routing can run in `rules`, `llm`, or `hybrid` mode, with `hybrid` using the LLM planner first and falling back to the rule-based router when the planner output is invalid.
Set `APP_TOOLS_LOG_PLANNER=true` to log raw planner output, parsed decisions, and fallback usage while tuning the planner locally.
The backend test suite includes a fixture-driven planner evaluation pass and prints a compact summary of tool-use, clarification, and fallback cases.

## Bedrock Provider

Set `APP_MODEL_PROVIDER=bedrock` to use Amazon Bedrock instead of Ollama.

Required configuration:

- `BEDROCK_REGION`
- `BEDROCK_MODEL_ID`

Bedrock now supports both:

- normal chat through `POST /api/chat`
- streaming chat through `POST /api/chat/stream`

For non-streaming chat, Bedrock responses now include provider metadata such as:

- `stopReason`
- `inputTokens`
- `outputTokens`
- `totalTokens`
- `durationMs`
- `providerLatencyMs`

Relevant environment variable:

- `APP_STORAGE_SESSIONS_DIRECTORY` default: `../data/sessions`
