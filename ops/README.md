# Ops

This directory contains local app runtime helpers.

Run the public lifecycle commands from the repository root:

```bash
./scripts/start.sh
./scripts/stop.sh
./scripts/restart.sh
./scripts/status.sh
./scripts/build.sh
./scripts/docker-start.sh
./scripts/docker-stop.sh
./scripts/docker-restart.sh
./scripts/docker-status.sh
./scripts/docker-check.sh
./scripts/docker-verify.sh
./scripts/docker-scan.sh
./scripts/docker-full-check.sh
```

`./scripts/stop.sh` stops PID-file-managed processes by default. Use `./scripts/stop.sh --all`
to also stop processes currently listening on the configured backend/frontend
ports. `./scripts/restart.sh` uses that broader stop behavior before starting the app.
`./scripts/build.sh` builds backend, frontend, and MCP artifacts without starting
or stopping the app.

The `docker-*` scripts are also public lifecycle commands under `scripts/`. They run
the full Docker Compose stack and intentionally stay separate from the host-run
`scripts/start.sh` / `scripts/stop.sh` workflow.

Docker backend image contract:

- the backend image is built from the repository root so it can include the
  Spring Boot backend, the built MCP server, and MCP tool scripts
- the image includes Node 20 because the backend starts the MCP server with
  `node`
- the image creates empty `/app/agents/reports/audit` and
  `/app/agents/reports/s3-cloudwatch` directories so MCP and storage health
  checks pass before any reports have been generated
- generated local report artifacts are excluded by `.dockerignore`

Use `./scripts/docker-status.sh` for diagnostics and `./scripts/docker-check.sh` for a
read-only smoke check that fails when the Docker stack is not usable.
Use `./scripts/docker-verify.sh` for the full non-read-only Docker workflow: stop
host-run processes, restart Docker Compose, show status, and run smoke checks.
Use `./scripts/docker-scan.sh` for a Trivy-based Docker image vulnerability scan.
By default, the scan includes the repository-owned backend/frontend images and
the external Qdrant vendor image. Use
`DOCKER_SCAN_INCLUDE_QDRANT=false ./scripts/docker-scan.sh` when you want to focus only
on images built from this codebase.
Treat the full scan as local-stack awareness and the owned-image-only scan as
the repository cleanliness check. Do not fork or patch Qdrant locally just to
clear vendor-image scan output; prefer upgrading to a newer vendor tag when one
is available.
Use `./scripts/docker-full-check.sh` when you want one command that runs both
`./scripts/docker-verify.sh` and `./scripts/docker-scan.sh`.

`./scripts/build.sh` runs tests unless explicitly skipped, so normal output includes
Maven and npm test/build progress. JVM/native-access warnings from Java
dependencies may still appear during backend tests. Application/controller stack
traces from expected negative-path tests should not be treated as normal build
noise.

or:

```bash
make start
make stop
make restart
make status
make build
make check-app
make docker-start
make docker-stop
make docker-restart
make docker-status
make docker-check
make docker-verify
make docker-scan
make docker-full-check
make test-ops
```

What belongs in `ops/`:

- backend-only startup helpers
- local stack smoke checks
- shared shell runtime helpers used by the `scripts/` lifecycle commands
- tests for those operational helpers

Run operational helper tests from the repository root:

```bash
make test-ops
```

What does not belong in `ops/`:

- human-facing lifecycle commands
- MCP tool scripts
- AWS audit/report generators
- MCP-facing report artifacts

Human-facing lifecycle commands stay under [`scripts/`](../scripts/README.md).
MCP/tool report scripts and artifacts stay under [`agents/`](../agents/README.md).
