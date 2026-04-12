# llm-pet-project

[![ci](https://github.com/jrodolfo/llm-pet-project/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jrodolfo/llm-pet-project/actions/workflows/ci.yml)
![java](https://img.shields.io/badge/java-21+-f89820)
![spring boot](https://img.shields.io/badge/spring%20boot-3.4-6db33f)
![react](https://img.shields.io/badge/react-frontend-61dafb)
![ollama](https://img.shields.io/badge/ollama-default%20provider-222222)
![bedrock](https://img.shields.io/badge/bedrock-optional%20provider-ff9900)
![mcp](https://img.shields.io/badge/mcp-local%20tools-0a7ea4)

Local GenAI lab for chat, tool orchestration, session memory, and AWS-oriented report workflows. The app uses a React frontend and a Spring Boot backend, with Ollama as the default model provider, Amazon Bedrock as an optional provider, and a local MCP server for AWS audit and report tooling.

## What It Does

- chat through a backend model-provider abstraction
- use Ollama by default and Amazon Bedrock optionally
- persist local conversation sessions as JSON
- resume, search, filter, import, export, and delete sessions
- run local MCP-backed AWS audit and report tools
- render structured report results and preview generated artifacts in-app
- expose OpenAPI, Swagger UI, and backend health endpoints

## Architecture

Primary chat path:

```text
React Frontend -> Spring Boot Backend -> Model Provider -> Ollama or Bedrock
```

Optional tool path:

```text
Spring Boot Backend -> Local MCP Server -> Shell Scripts -> AWS CLI / report files
```

## Project Structure

```text
llm-pet-project/
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

## Main Features

### Chat and Providers

- normal and streaming chat endpoints
- Ollama as the default provider
- Amazon Bedrock as an optional provider
- provider metadata in responses and saved session history

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
