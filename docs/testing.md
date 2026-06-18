# Testing

This document summarizes the automated and manual testing layers for `Local GenAI Lab`.

## Automated Suites

For the normal local pre-commit suite, run from the repository root:

```bash
make test
```

This runs operational helper tests, backend tests, and frontend tests. MCP
tests, frontend builds, and MCP tool script tests remain separate suites below.

For broader project verification, run:

```bash
make verify
```

This runs `make test`, frontend build, MCP tests/build, and MCP tool script
lint/tests. Use it before larger pushes or changes that touch multiple project
areas.

### CI And Local Mapping

GitHub CI mirrors the broader `make verify` contract, but keeps separate jobs so
failures are easier to diagnose. Each CI job calls top-level Makefile targets
instead of duplicating raw commands.

| CI job | Local target |
| --- | --- |
| `ops` | `make test-ops` |
| `backend` | `make test-backend` |
| `frontend` | `make test-frontend` and `make build-frontend` |
| `mcp` | `make test-mcp` and `make build-mcp` |
| `scripts` | `make test-scripts` |

If CI fails, first reproduce the failed job locally with the matching target.
Use `make verify` when you want to rerun the full broad suite.

### Optional Live RAG + Qdrant Smoke Test

The normal test suite does not require Docker, Qdrant, Ollama, or a live local
backend. When you specifically want to verify the full local Qdrant RAG path,
start the app in Qdrant vector mode:

```bash
RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh
```

Then run:

```bash
make test-rag-qdrant-smoke
```

This target checks backend health, RAG status, Qdrant reachability, the Ollama
embedding model, index rebuild, Qdrant point count, and one RAG query using
`vector:qdrant`. It is intentionally opt-in and local/manual today.

The current GitHub CI workflow does not provision Qdrant, install Ollama, or
pull the `nomic-embed-text` embedding model. Keep this target out of default CI
unless the workflow is later updated to provide those runtime dependencies.

### Backend

The backend targets Java 21. Before running backend tests, confirm the active
JDK with:

```bash
java -version
```

Run:

```bash
cd backend
mvn test
```

Covers:
- chat orchestration and tool-routing regressions
- Ollama, Bedrock, and Hugging Face provider behavior
- session persistence, filtering, export/import, and mixed-provider metadata
- artifact preview and path-safety behavior
- controller error handling, health/model APIs, and streamed chat MVC smoke coverage

Backend tests are split into four practical layers:

1. Service and unit-style tests
   Main examples:
   - `backend/src/test/java/net/jrodolfo/llm/service/*.java`
   - `backend/src/test/java/net/jrodolfo/llm/config/*.java`
   - `backend/src/test/java/net/jrodolfo/llm/health/*.java`

   Use this layer for:
   - orchestration, routing, normalization, and prompt-building logic
   - session persistence rules
   - provider-status and model-discovery behavior
   - focused config and health-check logic

2. Provider and client contract tests
   Main examples:
   - `backend/src/test/java/net/jrodolfo/llm/client/*.java`
   - `backend/src/test/java/net/jrodolfo/llm/provider/*.java`

   Use this layer for:
   - provider-specific request/response behavior
   - provider error classification
   - client-side caching and timeout behavior
   - provider adapter contract checks without a full Spring context

3. Focused controller and MVC tests
   Main examples:
   - `backend/src/test/java/net/jrodolfo/llm/controller/*.java`

   Use this layer for:
   - request validation
   - status-code and response-shape checks
   - controller-specific error handling
   - SSE/controller behavior without paying for a full application context

4. Spring Boot smoke and wiring tests
   Main examples:
   - `backend/src/test/java/net/jrodolfo/llm/ApiSmokeIntegrationTest.java`
   - `backend/src/test/java/net/jrodolfo/llm/LlmApplicationTests.java`

   Use this layer for:
   - real application wiring across the highest-value endpoints
   - end-to-end MVC stack checks with the Spring context running
   - critical startup and smoke coverage

Practical rule:
- prefer service/provider/controller tests when a full Spring context is not needed
- prefer `ApiSmokeIntegrationTest` only for critical endpoint wiring
- do not add heavy `@SpringBootTest` coverage for behavior that is already well covered at a narrower layer

Use this suite when changing:
- Spring Boot controllers or services
- provider integration code
- session persistence behavior
- artifact/report APIs

### Frontend

Run:

```bash
cd frontend
npm test -- --run
npm run build
```

Covers:
- provider/model selection behavior
- streamed and non-streamed chat UI flows
- session sidebar, reopening saved sessions, and mixed-provider rendering
- explicit streaming tool-phase handling
- message formatting, tool provenance display, tool result cards, loading states, provider-status refresh behavior, and artifact panel empty states

Frontend tests are split into three layers:

1. Unit and component-style tests
   Main examples:
   - `frontend/src/components/*.test.jsx`
   - `frontend/src/pages/Home.test.jsx`

   Use this layer for:
   - narrow UI state transitions
   - conditional rendering
   - formatting and metadata display
   - fast behavior checks that do not need backend-shaped request flows

2. API integration tests
   Main example:
   - `frontend/src/api/api.integration.test.js`

   Use this layer for:
   - request/response parsing
   - SSE event parsing
   - error propagation from the API modules

3. MSW-backed page integration tests
   Main example:
   - `frontend/src/pages/Home.integration.test.jsx`

   Use this layer for:
   - backend-shaped user flows through the real API modules
   - chat success and failure paths
   - streaming SSE flows
   - session reopen and export flows
   - artifact preview flows

Supporting test utilities:
- `frontend/src/test/setup.js`
  Wires the shared MSW lifecycle into Vitest with `server.listen()`, `server.resetHandlers()`, and `server.close()`.
- `frontend/src/test/mswServer.js`
  Owns the reusable MSW server plus SSE helpers such as `sseResponse(...)`, `sseStreamResponse(...)`, and `sseEventChunk(...)`.
- `frontend/src/test/mswHandlers.js`
  Owns shared backend-shaped runtime handlers used by the page integration tests.

Practical rule:
- prefer MSW-backed tests for full request/response user flows
- prefer direct module mocks for narrow local UI behavior
- do not add broad module-mocked tests for flows already covered well through `Home.integration.test.jsx`

Use this suite when changing:
- `Home.jsx`
- message rendering
- provider/model controls
- session list or chat layout behavior

### MCP

Run:

```bash
cd mcp
npm test
npm run build
```

Covers:
- MCP tool input/output contracts
- report discovery and summary-reading behavior against realistic fixture directories
- path-safety checks for report access
- malformed summary/report edge cases

Use this suite when changing:
- MCP tool handlers
- report discovery or parsing logic
- MCP tool contracts

### Shell Scripts

Run:

```bash
make test-ops
cd scripts
make lint
./tests/test.sh
./tests/test-s3-cloudwatch.sh
```

Covers:
- shell behavior for the AWS audit and S3 CloudWatch report scripts
- unified backend startup script linting and `.env` precedence behavior
- app lifecycle helper behavior, including `status.sh` RAG/Ollama readiness output

Use this suite when changing:
- `aws-region-audit-report.sh`
- `aws-s3-cloudwatch-report.sh`
- helper startup/check scripts

## Manual Smoke Tests

These flows are still worth running manually after meaningful changes:

1. Plain chat with Ollama
   Verify normal non-tool prompts and multi-turn context.

2. Plain chat with Bedrock
   Verify multi-turn context and model switching without restarting the backend.

3. Plain chat with Hugging Face
   Verify configured candidate validation, status-banner details, and normal hosted chat replies.

4. Streaming chat
   Verify partial tokens appear before completion and metadata/provenance settle correctly at the end.

5. Tool-assisted chat
   Verify prompts such as `Please audit my AWS account.` or S3 metrics requests execute the MCP-backed tools, show streaming tool phases when applicable, and render tool results correctly.

6. Provider switching in one session
   Verify the configured providers in the current backend process can all answer in the same saved session and that the UI shows per-turn provider/model provenance.

7. Session reopen and export/import
   Verify saved sessions reopen correctly and JSON/Markdown export still reflect the stored provider/model metadata.

8. Artifact preview
   Verify report summary and report preview actions open the expected artifacts without loading the entire file into memory, and that the artifact inspector titles and empty states make sense.

9. Frontend test architecture drift
   If you add a new major frontend flow, decide explicitly whether it belongs in:
   - `Home.test.jsx`
   - `api.integration.test.js`
   - `Home.integration.test.jsx`

   Default:
   - user flow through backend endpoints -> MSW-backed integration test
   - local UI-only behavior -> mocked unit/component test

10. Cross-provider evaluation
   Use:
   - [provider-evaluation-template.md](./provider-evaluation-template.md)

   Run the same prompt set against:
   - Ollama
   - Bedrock
   - Hugging Face

   Compare:
   - objective timing
   - perceived responsiveness
   - streaming behavior
   - tool-assisted behavior
   - clarification flow
   - failure-message quality

   Promote only repeated issues into the backlog. Do not react to one-off provider noise.

## Known Non-Automated Areas

The following still depend primarily on manual or environment-specific validation:

- real Ollama runtime behavior with installed local models
- real Bedrock credentials, regional access, and inference-profile availability
- real Hugging Face token, hosted model access, and candidate validation behavior
- shell-script execution against a live AWS account
- model-specific answer quality differences after tool grounding
- end-to-end startup behavior when local services are down or partially configured

## Quick Checklist

For most code changes:

1. Run the relevant automated suite for the changed area.
2. Run at least one matching manual smoke test.
3. If provider, session, or MCP behavior changed, prefer running both the automated suite and a short end-to-end UI check.
