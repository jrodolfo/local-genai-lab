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

## Tests

Run:

```bash
npm test -- --run
npm run build
```

Frontend tests are organized into three practical layers:

1. Narrow unit/component tests
   Main files:
   - `src/components/*.test.jsx`
   - `src/pages/Home.test.jsx`

   Use these for local UI behavior, conditional rendering, and formatting details.

2. API integration tests
   Main file:
   - `src/api/api.integration.test.js`

   Use these for request parsing, SSE parsing, and API-level error handling.

3. MSW-backed page integration tests
   Main file:
   - `src/pages/Home.integration.test.jsx`

   Use these for backend-shaped user flows such as:
   - chat success and failure
   - streaming chat and tool phases
   - session reopen/export
   - artifact preview

Shared test setup lives in:
- `src/test/setup.js`
- `src/test/mswServer.js`
- `src/test/mswHandlers.js`

Practical rule:
- if the behavior is a real UI flow through frontend API modules, prefer MSW
- if the behavior is narrow and local to the component, prefer direct mocks

## Notes

- the UI is session-oriented; reopened sessions restore saved messages and metadata
- assistant turns show provider/model provenance by default
- technical details such as timings, stop reasons, and token counts are hidden by default and can be enabled from the UI
- if no Ollama models are installed locally, the composer shows a pull hint and disables sending
- provider switching is runtime-driven by the backend `/api/models` response rather than a frontend-only toggle
- the provider selector only shows providers configured in the running backend process
- the provider status banner explains readiness, rejected Hugging Face candidates, and common provider-specific issues
- streamed replies can enrich the final assistant message with provider metadata when the backend emits completion metadata
