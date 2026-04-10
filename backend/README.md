# Backend

![java](https://img.shields.io/badge/java-21+-f89820)
![spring boot](https://img.shields.io/badge/spring%20boot-3.4-6db33f)
![ollama](https://img.shields.io/badge/ollama-chat%20proxy-222222)
![mcp](https://img.shields.io/badge/mcp-optional%20integration-0a7ea4)

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
