# Documentation Review Checklist

Use this checklist when reviewing documentation after implementation changes.
The goal is to catch drift, contradictions, stale commands, and confusing
terminology. Do not rewrite large sections for style only.

## Review Scope

Review current-state documentation first:

- `README.md`
- `backend/README.md`
- `frontend/README.md`
- `ops/README.md`
- `scripts/README.md`
- `docs/architecture.md`
- `docs/architecture-overview.md`
- `docs/architecture-walkthrough.md`
- `docs/providers.md`
- `docs/testing.md`
- RAG and Qdrant docs under `docs/`

Treat ADRs as historical records. Do not rewrite accepted ADRs just because
later work extended the design. Instead, make sure `docs/adr/README.md` explains
the sequence clearly.

## Provider Terminology

Check that provider wording is consistent:

- `Ollama`: local runtime
- `Amazon Bedrock`: remote provider API
- `Hugging Face`: remote provider API
- `configured provider`: provider enabled in the running backend process

Avoid wording that mixes different concepts:

- do not use `hosted provider` for Hugging Face in current-state docs
- do not use `managed provider` as a generic Bedrock label in current-state docs
- do not imply Bedrock is only `configured` while Hugging Face is `hosted`

Useful checks:

```bash
rg -n 'hosted provider|hosted%20provider|managed provider|managed providers|using managed providers' README.md backend/README.md docs -g '*.md'
```

## RAG And Qdrant Status

Confirm current docs say RAG is implemented and describe all retrieval targets:

- `Lexical`
- `Vector - In Memory`
- `Vector - Qdrant`

Confirm docs say:

- RAG uses `/api/rag/*`
- RAG remains separate from `/api/chat`
- RAG does not invoke MCP tools
- lexical retrieval is the default
- Qdrant is optional for normal startup and lexical RAG
- Qdrant is required for explicit Qdrant vector mode
- the RAG index can be built lazily on the first question
- `Rebuild Index` is optional for first use and useful after corpus/config changes

Useful checks:

```bash
rg -n 'experimental RAG|phase-1 RAG workspace|fixed phase-1|not yet wired|currently a placeholder|external vector database' README.md docs backend/README.md frontend/README.md -g '*.md'
```

## Architecture Alignment

Confirm these docs agree:

- `docs/architecture-overview.md`
- `docs/architecture.md`
- `docs/architecture-walkthrough.md`

The Mermaid diagram should include:

- Agent workspace
- RAG workspace
- Chat flow
- RAG flow
- Provider Registry
- sessions
- reports/artifacts
- MCP tools
- Ollama embeddings
- Qdrant
- lexical, in-memory vector, and Qdrant retrieval paths

## Lifecycle And Script Commands

Root app lifecycle commands are:

```bash
./start.sh
./stop.sh
./restart.sh
./status.sh
./build.sh
./docker-start.sh
./docker-stop.sh
./docker-restart.sh
./docker-status.sh
```

Root Makefile commands are:

```bash
make test
make verify
make build
make check-app
make test-rag-qdrant-smoke
```

Check that:

- `scripts/README.md` documents MCP/tool shell scripts, not app lifecycle
- `ops/README.md` documents local runtime helpers
- Docker lifecycle scripts are documented as root-level public lifecycle
  commands, not as `ops/` or `scripts/` commands
- docs do not reference removed app lifecycle commands such as `run-backend.sh`
  or `make run-backend`
- commands using `./ops/start-backend-helper.sh` are meant to run from the
  repository root

Useful checks:

```bash
rg -n 'run-backend.sh|make run-backend|cd scripts.*ops/start-backend-helper|start-backend-helper.sh' README.md docs backend/README.md frontend/README.md ops/README.md scripts/README.md -g '*.md'
```

## Frontend And Backend README Alignment

Check `frontend/README.md` includes:

- Agent workspace
- RAG workspace
- RAG API endpoints used by the frontend
- RAG tests and MSW-backed integration coverage where relevant

Check `backend/README.md` includes:

- Agent chat endpoints
- RAG endpoints
- session endpoints
- artifact endpoints
- tool endpoints
- provider/model discovery
- provider status
- RAG retrieval targets
- Qdrant settings

## Testing Documentation

Check `docs/testing.md` matches current test commands:

- default tests do not require Docker, Qdrant, Ollama, or cloud credentials
- `make test-rag-qdrant-smoke` is documented as optional/manual
- GitHub CI is not described as provisioning Qdrant or Ollama unless the workflow
  actually does so

## ADR Handling

Check `docs/adr/README.md` tells readers ADRs are historical records.

For RAG:

- ADR 0012: original isolated lexical RAG workspace
- ADR 0013: vector retrieval with Ollama embeddings and Qdrant
- ADR 0014: retrieval comparison without session side effects

Do not edit historical ADR content unless it is misleading even in historical
context. Prefer clarifying the ADR README.

## Final Verification

Run:

```bash
git diff --check
```

For large documentation passes, also run focused `rg` scans for the stale terms
listed in this checklist and inspect the results manually. Some hits inside ADRs
may be acceptable historical context.
