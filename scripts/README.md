# Scripts

Human-facing lifecycle and Docker commands for running Local GenAI Lab from the
repository root.

This directory owns commands that a developer runs directly to start, stop,
inspect, build, or validate the app. The root `Makefile` calls these scripts so
`make start` and `./scripts/start.sh` stay aligned.

## Commands

Host-run app lifecycle:

```bash
./scripts/start.sh
./scripts/stop.sh
./scripts/restart.sh
./scripts/status.sh
./scripts/build.sh
```

Docker lifecycle:

```bash
./scripts/docker-start.sh
./scripts/docker-stop.sh
./scripts/docker-restart.sh
./scripts/docker-status.sh
./scripts/docker-check.sh
./scripts/docker-verify.sh
./scripts/docker-scan.sh
./scripts/docker-full-check.sh
./scripts/release-check.sh
```

Release validation:

```bash
./scripts/release-check.sh
RELEASE_CHECK_DOCKER=true ./scripts/release-check.sh
```

The default release check runs tests, broader verification, dependency freshness
reporting, and whitespace checks. Docker verification and image scanning are
opt-in with `RELEASE_CHECK_DOCKER=true` so the release gate can still run when
Docker Desktop or Trivy is unavailable.

## Folder Boundaries

- `scripts/`: human-facing lifecycle, build, and Docker commands.
- `ops/`: support libraries, local smoke checks, and shell tests behind the lifecycle commands.
- `agents/`: MCP/agent tool scripts, dependency freshness reporting, and generated report artifacts.

Root-level shell scripts are intentionally not allowed. `make test-ops` includes
a layout guardrail that fails if a `*.sh` file appears in the repository root.
