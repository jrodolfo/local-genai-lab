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
./scripts/docker-sanity-check.sh
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

Docker-based AWS Agent tools are opt-in because they mount host AWS
configuration into the backend container. To enable them for your machine
without passing flags to every Docker command:

```bash
cp .env.docker-aws-tools.example .env.docker-aws-tools
```

Then edit `AWS_PROFILE`, `AWS_REGION`, and `LOCAL_GENAI_LAB_AWS_DIR` if needed.
The file is ignored by Git. When enabled, `docker-start.sh` and
`docker-restart.sh` use the AWS compose override automatically during startup.

`docker-sanity-check.sh` is the fastest Docker preflight. It verifies that the
Docker daemon and Compose plugin are reachable before you spend time on image
builds, Compose startup, or Trivy scans. To also prove Docker can run a
container, use:

```bash
DOCKER_SANITY_RUN_HELLO_WORLD=true ./scripts/docker-sanity-check.sh
```

Release validation:

```bash
./scripts/release-check.sh
RELEASE_CHECK_DOCKER=true ./scripts/release-check.sh
```

The default release check runs tests, broader verification, dependency freshness
reporting, and whitespace checks. Docker verification and image scanning are
opt-in with `RELEASE_CHECK_DOCKER=true` or `make release-check-docker` so the
default release gate can still run when Docker Desktop, Docker Engine, or Trivy
is unavailable.
When Docker is requested, the script preflights Docker, Docker Compose, and
Trivy before running expensive tests.

## Folder Boundaries

- `scripts/`: human-facing lifecycle, build, and Docker commands.
- `ops/`: support libraries, local smoke checks, and shell tests behind the lifecycle commands.
- `agents/`: MCP/agent tool scripts, dependency freshness reporting, and generated report artifacts.

Root-level shell scripts are intentionally not allowed. `make test-ops` includes
a layout guardrail that fails if a `*.sh` file appears in the repository root.
