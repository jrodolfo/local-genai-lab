# Backend

Spring Boot API that proxies requests to Ollama.

## Run

```bash
mvn spring-boot:run
```

## Endpoints

- `POST /api/chat`
- `POST /api/chat/stream` (SSE)
- `GET /api/tools`
- `POST /api/tools/aws-region-audit`
- `POST /api/tools/s3-cloudwatch-report`
- `GET /api/tools/reports`
- `POST /api/tools/reports/read`

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

- `MCP_ENABLED`
- `MCP_COMMAND` default: `node`
- `MCP_WORKING_DIRECTORY` default: `../mcp`
- `MCP_ARG_1` default: `dist/index.js`
- `MCP_STARTUP_TIMEOUT_SECONDS`
- `MCP_TOOL_TIMEOUT_SECONDS`
