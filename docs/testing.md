# Testing

This document summarizes the automated and manual testing layers for `Local GenAI Lab`.

## Automated Suites

### Backend

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
cd scripts
make lint
./tests/test.sh
./tests/test-s3-cloudwatch.sh
```

Covers:
- shell behavior for the AWS audit and S3 CloudWatch report scripts
- unified backend startup script linting and `.env` precedence behavior

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
