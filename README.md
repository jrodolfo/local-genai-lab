# local-genai-lab

[![ci](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml)
![java](https://img.shields.io/badge/java-21+-f89820)
![spring boot](https://img.shields.io/badge/spring%20boot-3.4-6db33f)
![react](https://img.shields.io/badge/react-frontend-61dafb)
![ollama](https://img.shields.io/badge/ollama-default%20provider-222222)
![bedrock](https://img.shields.io/badge/bedrock-optional%20provider-ff9900)
![mcp](https://img.shields.io/badge/mcp-local%20tools-0a7ea4)

Local-first GenAI lab for building and testing tool-assisted chat workflows, not just a chatbot UI. It combines a React frontend, a Spring Boot orchestration backend, local and managed model providers, persistent session memory, and MCP-backed AWS tooling in one full-stack repository.

Why it is interesting:

- tool-assisted chat with backend-side orchestration instead of direct frontend-to-model calls
- provider abstraction with Ollama by default and Amazon Bedrock as an optional runtime
- persistent session memory with resume, search, filter, import, and export flows
- MCP-backed local tool execution for AWS audits, reports, and artifact generation
- structured report rendering, artifact preview, streaming responses, and API observability

## Architecture

Primary chat path:

```text
React Frontend -> Spring Boot Backend -> Ollama or Bedrock
```

Tool-assisted chat path:

```text
React Frontend -> Spring Boot Backend -> Local MCP Server -> Shell Scripts -> AWS CLI / report files -> Spring Boot Backend -> Ollama or Bedrock
```

Immediate backend response path:

```text
React Frontend -> Spring Boot Backend -> clarification or tool-failure response
```

## Project Structure

```text
local-genai-lab/
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── README.md
├── frontend/
│   ├── src/
│   ├── package.json
│   └── README.md
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

- Java 21+
- Maven 3.9+
- Node 20+
- Ollama installed locally for the default provider
- Docker + Docker Compose, optional
- AWS CLI v2 + `jq` + valid AWS credentials, only for AWS shell tools and local MCP-backed report flows

## Quick Start

### 1. Pull a local model

```bash
ollama pull llama3:8b
ollama run llama3:8b
```

Ollama should be reachable at `http://localhost:11434`.

### 2. Start the backend

```bash
cd backend
mvn spring-boot:run
```

Backend URLs:

- API root: `http://localhost:8080`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health: `http://localhost:8080/actuator/health`
- Info: `http://localhost:8080/actuator/info`

`GET /actuator` redirects to `/actuator/health`. Swagger excludes `/actuator/**` so the generated API docs stay focused on the application API.

### 3. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend URL:

- `http://localhost:5173`

### 4. Optional: build the local MCP server

```bash
cd mcp
npm install
npm run build
```

MCP is enabled by default in the backend. To run without it, set `MCP_ENABLED=false`.

## Docker

Keep Ollama running on the host first, then:

```bash
docker compose up --build
```

- frontend: `http://localhost:3000`
- backend: `http://localhost:8080`

## Configuration Overview

The most important backend settings are:

- `APP_MODEL_PROVIDER` default: `ollama`
- `OLLAMA_DEFAULT_MODEL` default: `llama3:8b`
- `BEDROCK_REGION` default: `us-east-1`
- `BEDROCK_MODEL_ID` default: empty
- `MCP_ENABLED` default: `true`
- `APP_TOOLS_ROUTING_MODE` default: `hybrid`
- `APP_STORAGE_SESSIONS_DIRECTORY` default: `data/sessions`
- `APP_STORAGE_REPORTS_DIRECTORY` default: `scripts/reports`

The storage defaults are resolved from the project root so they stay stable whether the backend starts from `backend/` or the repository root.
You can also point `APP_STORAGE_REPORTS_DIRECTORY` to an absolute path outside the repository if you want report artifacts stored elsewhere.

## Main Features

### Chat and Providers

- normal and streaming chat endpoints
- Ollama as the default provider
- Amazon Bedrock as an optional provider
- provider metadata in responses and saved session history
- typed JSON SSE events for streaming chat

### Sessions

- local JSON-backed conversation memory
- generated session titles and summaries
- session sidebar with search and filters
- JSON and Markdown export
- JSON import with collision-safe session ids
- backend-managed opaque session ids with strict validation

### Tools and Artifacts

- local MCP-backed AWS audit and reporting flows
- LLM-assisted tool routing with fallback
- multi-turn clarification for missing tool inputs
- structured report cards in the UI
- read-only artifact preview and file listing under `scripts/reports/`

Artifact API note:

- the configured reports directory may be inside or outside the repository
- artifact endpoints accept only paths relative to that configured reports directory
- absolute artifact paths are rejected intentionally so the backend keeps a strict read-only boundary

## Shell Scripts

The shell tooling lives under [`scripts/`](./scripts). Useful entrypoints:

```bash
cd scripts
make help
make check-app
make test
make audit
make s3-cloudwatch BUCKET=example.com
```

`make check-app` verifies backend, frontend, and Ollama availability with sensible local defaults.

## Documentation Map

- [backend/README.md](./backend/README.md): backend API, provider config, MCP integration, Actuator, sessions, Bedrock notes
- [frontend/README.md](./frontend/README.md): frontend-specific details
- [scripts/README.md](./scripts/README.md): shell tooling, report formats, smoke checks
- [mcp/README.md](./mcp/README.md): local MCP server details

## Notes for Heavier Models

- `codellama:70b` is optional and not the recommended first-run default
- larger local models can be much slower and more memory intensive
- if you use a heavier model, you may need to raise backend read timeouts

## Contact

- GitHub: https://github.com/jrodolfo
- Webpage: https://jrodolfo.net
