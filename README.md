# llm-pet-project

[![ci](https://github.com/jrodolfo/llm-pet-project/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jrodolfo/llm-pet-project/actions/workflows/ci.yml)
![java](https://img.shields.io/badge/java-21+-f89820)
![spring boot](https://img.shields.io/badge/spring%20boot-3.4-6db33f)
![react](https://img.shields.io/badge/react-frontend-61dafb)
![ollama](https://img.shields.io/badge/ollama-local%20llm-222222)
![mcp](https://img.shields.io/badge/mcp-local%20tools-0a7ea4)

Proof-of-concept app to chat with local LLMs via Ollama, with optional local MCP-backed tooling for AWS audit and report workflows and local JSON-backed conversation memory.

Architecture:

React Frontend -> Spring Boot Backend -> Ollama REST API -> Local LLM

Optional tool path:

Spring Boot Backend -> Local MCP Server -> Shell Scripts -> AWS CLI / report files

## Project Structure

```text
llm-pet-project/
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── README.md
├── frontend/
│   ├── src/
│   └── package.json
├── scripts/
│   ├── reports/
│   ├── tests/
│   ├── Makefile
│   └── README.md
├── mcp/
│   ├── src/
│   ├── package.json
│   └── README.md
├── docker-compose.yml
└── README.md
```

## Prerequisites

- macOS with Ollama installed and running
- Java 21+
- Maven 3.9+
- Node 20+
- Docker + Docker Compose (optional)
- AWS CLI v2 + `jq` + valid AWS credentials
  Required only for the optional local MCP server and the shell-based AWS audit/report tools.

## 1. Pull and Run a Model in Ollama

```bash
ollama pull codellama:70b
ollama run codellama:70b
```

Ollama API must be available at `http://localhost:11434`.

## 2. Run Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run
```

Backend runs on `http://localhost:8080`.

### Backend API

`POST /api/chat`

Request:

```json
{
  "message": "Explain recursion",
  "model": "codellama:70b",
  "sessionId": "optional-existing-session-id"
}
```

Response:

```json
{
  "response": "...",
  "model": "codellama:70b",
  "sessionId": "generated-or-reused-session-id",
  "tool": null
}
```

Streaming endpoint: `POST /api/chat/stream` (SSE).
The streaming path emits an initial `metadata` event that can include the active `sessionId` and optional tool provenance before token events begin.

Optional MCP-backed tool endpoints:

- `GET /api/tools`
- `POST /api/tools/aws-region-audit`
- `POST /api/tools/s3-cloudwatch-report`
- `GET /api/tools/reports`
- `POST /api/tools/reports/read`

Session endpoints:

- `GET /api/sessions`
- `GET /api/sessions/{sessionId}`
- `DELETE /api/sessions/{sessionId}`

## 3. Run Frontend (React)

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:5173`.

The UI now includes a local session sidebar where you can:

- start a new chat
- reopen a saved session
- delete a saved session

## 4. Run the Local MCP Server

The repository includes a separate local MCP server under [`mcp/`](./mcp) that wraps the shell tools under [`scripts/`](./scripts).

Build it first:

```bash
cd mcp
npm install
npm run build
```

Run it directly:

```bash
cd mcp
npm start
```

Or let the backend launch it as a subprocess by enabling MCP when starting Spring Boot:

```bash
cd backend
MCP_ENABLED=true mvn spring-boot:run
```

The MCP server is intentionally separate from the Spring backend so shell execution and report access stay isolated from the chat API.

## 5. Shell Tools

The [`scripts/`](./scripts) directory contains the imported shell-based AWS tooling and its local tests.

Useful entrypoints:

```bash
cd scripts
make help
make test
make audit
make s3-cloudwatch BUCKET=example.com
```

See [`scripts/README.md`](./scripts/README.md) for the script-specific options and report formats.

## 6. Run with Docker Compose

Keep Ollama running on the host machine first. Then:

```bash
docker compose up --build
```

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`

Compose uses `host.docker.internal:11434` so backend container can reach host Ollama.

## Environment Variables (Backend)

- `OLLAMA_BASE_URL` (default: `http://localhost:11434`)
- `OLLAMA_DEFAULT_MODEL` (default: `codellama:70b`)
- `OLLAMA_CONNECT_TIMEOUT_SECONDS` (default: `10`)
- `OLLAMA_READ_TIMEOUT_SECONDS` (default: `240`)
- `MCP_ENABLED` (default: `false`)
- `MCP_COMMAND` (default: `node`)
- `MCP_WORKING_DIRECTORY` (default: `../mcp`)
- `MCP_ARG_1` (default: `dist/index.js`)
- `MCP_STARTUP_TIMEOUT_SECONDS` (default: `10`)
- `MCP_TOOL_TIMEOUT_SECONDS` (default: `120`)
- `APP_STORAGE_SESSIONS_DIRECTORY` (default: `../data/sessions`)

## Notes

- The default chat flow does not require MCP.
- The backend can call MCP tools only when `MCP_ENABLED=true`.
- The MCP server uses local `stdio` transport and is designed for private, local execution.
- The `scripts/reports/` tree contains generated local artifacts and is intentionally reused by both the shell tools and the MCP wrappers.
- Conversation history is stored locally as JSON files under [`data/sessions/`](./data/sessions).
- The frontend reuses the returned `sessionId` automatically so follow-up prompts keep local context.
- Session titles in the sidebar are derived from the first user message in each stored session.

## Notes for 70B Models

- 70B 4-bit models can be memory intensive.
- Keep other heavy apps closed while testing.
- Increase backend read timeout if generation is slow.

## Next Enhancements

- Add authentication
- Improve prompt templates/system prompts
- Add metrics and tracing
- Support multiple model providers

## Contact

For issues or inquiries, feel free to contact the maintainer:

- Name: Rod Oliveira
- Role: Software Developer
- Email: jrodolfo@gmail.com
- GitHub: https://github.com/jrodolfo
- LinkedIn: https://www.linkedin.com/in/rodoliveira
- Webpage: https://jrodolfo.net
