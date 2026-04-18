# Frontend

[![license](https://img.shields.io/badge/license-MIT-blue)](../LICENSE)

React + Vite client for the local chat UI.

## Run

```bash
npm install
npm run dev
```

Default local URL:

- `http://localhost:5173`

## What It Does

- sends normal and streaming chat requests to the backend
- loads provider and model selectors dynamically from backend-provided available options
- keeps the active `sessionId` across turns
- shows a session sidebar with search and filters
- supports session import, export, delete, and resume
- renders structured MCP report cards for supported tool results
- previews local report artifacts in-app through the backend artifact endpoints
- shows provider/model provenance on assistant turns by default
- keeps deeper timing and token details behind the UI debug toggle

## Backend Integration

The frontend expects the Spring Boot backend to be running at:

- `http://localhost:8080`

It uses:

- `/api/chat`
- `/api/chat/stream`
- `/api/models`
- `/api/sessions`
- `/api/artifacts/*`
- `/api/tools/*`

## Notes

- the UI is session-oriented; reopened sessions restore saved messages and metadata
- assistant turns show provider/model provenance by default
- technical details such as timings, stop reasons, and token counts are hidden by default and can be enabled from the UI
- if no Ollama models are installed locally, the composer shows a pull hint and disables sending
- provider switching is runtime-driven by the backend `/api/models` response rather than a frontend-only toggle
- streamed replies can enrich the final assistant message with provider metadata when the backend emits completion metadata
