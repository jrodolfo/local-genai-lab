# Ops

This directory contains local app runtime helpers.

Use the repository root for the public lifecycle commands:

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
./docker-check.sh
./docker-verify.sh
./docker-scan.sh
./docker-full-check.sh
```

`./stop.sh` stops PID-file-managed processes by default. Use `./stop.sh --all`
to also stop processes currently listening on the configured backend/frontend
ports. `./restart.sh` uses that broader stop behavior before starting the app.
`./build.sh` builds backend, frontend, and MCP artifacts without starting
or stopping the app.

The `docker-*` scripts are also root-level public lifecycle commands. They run
the full Docker Compose stack and intentionally stay separate from the host-run
`start.sh` / `stop.sh` workflow.

Docker backend image contract:

- the backend image is built from the repository root so it can include the
  Spring Boot backend, the built MCP server, and MCP tool scripts
- the image includes Node 20 because the backend starts the MCP server with
  `node`
- the image creates empty `/app/scripts/reports/audit` and
  `/app/scripts/reports/s3-cloudwatch` directories so MCP and storage health
  checks pass before any reports have been generated
- generated local report artifacts are excluded by `.dockerignore`

Use `./docker-status.sh` for diagnostics and `./docker-check.sh` for a
read-only smoke check that fails when the Docker stack is not usable.
Use `./docker-verify.sh` for the full non-read-only Docker workflow: stop
host-run processes, restart Docker Compose, show status, and run smoke checks.
Use `./docker-scan.sh` for a Trivy-based Docker image vulnerability scan.
By default, the scan includes the repository-owned backend/frontend images and
the external Qdrant vendor image. Use
`DOCKER_SCAN_INCLUDE_QDRANT=false ./docker-scan.sh` when you want to focus only
on images built from this codebase.
Use `./docker-full-check.sh` when you want one command that runs both
`./docker-verify.sh` and `./docker-scan.sh`.

`./build.sh` runs tests unless explicitly skipped, so normal output includes
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
- shared shell runtime helpers used by the top-level lifecycle scripts
- tests for those operational helpers

Run operational helper tests from the repository root:

```bash
make test-ops
```

What does not belong in `ops/`:

- MCP tool scripts
- AWS audit/report generators
- MCP-facing report artifacts

Those stay under [`scripts/`](../scripts/README.md).
