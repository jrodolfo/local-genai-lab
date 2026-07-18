SHELL := /bin/bash

.PHONY: help start stop restart status build check-app local-verify docker-sanity-check docker-start docker-stop docker-restart docker-status docker-logs docker-tunnel-info docker-check docker-aws-preflight docker-go docker-verify docker-scan docker-full-check dependency-freshness release-check release-check-docker prepare-release clean-ds-store test verify test-ops test-backend test-frontend test-rag-qdrant-smoke build-frontend test-mcp build-mcp test-scripts

help:
	@printf '%s\n' \
		'Available targets:' \
		'  make start                  Start backend and frontend in the background' \
		'  make stop                   Stop background backend and frontend processes' \
		'  make restart                Restart backend and frontend' \
		'  make status                 Show process, URL, and log status' \
		'  make build                  Build backend, frontend, and MCP artifacts' \
		'  make check-app              Run the local stack smoke check' \
		'  make local-verify           Run explicit local verification with /tmp logs' \
		'  make docker-sanity-check    Check Docker daemon and Compose availability' \
		'  make docker-start           Start backend, frontend, and Qdrant with Docker Compose' \
		'  make docker-stop            Stop the Docker Compose stack' \
		'  make docker-restart         Restart the Docker Compose stack' \
		'  make docker-status          Show Docker Compose service status' \
		'  make docker-logs            Follow Docker Compose service logs' \
		'  make docker-tunnel-info     Print SSH tunnel commands for remote Docker access' \
		'  make docker-check           Smoke-check the running Docker Compose stack' \
		'  make docker-aws-preflight   Verify AWS tools from the Docker backend container' \
		'  make docker-go              Build, restart, and validate Docker Agent testing' \
		'  make docker-verify          Restart, inspect, and smoke-check Docker mode' \
		'  make docker-scan            Scan Docker images for known vulnerabilities' \
		'  make docker-full-check      Run Docker verification and Docker image scan' \
		'  make dependency-freshness   Report Maven, npm, and Docker dependency freshness' \
		'  make release-check          Run the local pre-release validation gate' \
		'  make release-check-docker   Run release check with Docker verification/scan' \
		'  make prepare-release VERSION=vX.Y.Z  Run guided release preparation' \
		'  make clean-ds-store         Remove macOS .DS_Store files from the repo tree' \
		'  make test                   Run ops, backend, and frontend tests' \
		'  make verify                 Run broader project verification' \
		'  make test-ops               Run operational shell helper tests' \
		'  make test-backend           Run backend tests' \
		'  make test-frontend          Run frontend tests' \
		'  make test-rag-qdrant-smoke  Run optional live RAG + Qdrant smoke test' \
		'  make build-frontend         Build the frontend' \
		'  make test-mcp               Run MCP tests' \
		'  make build-mcp              Build MCP server' \
		'  make test-scripts           Run MCP tool script lint/tests'

start:
	@./scripts/start.sh

stop:
	@./scripts/stop.sh

restart:
	@./scripts/restart.sh

status:
	@./scripts/status.sh

build:
	@./scripts/build.sh

check-app:
	@./ops/check-app.sh

local-verify:
	@./scripts/local-verify.sh

docker-sanity-check:
	@./scripts/docker-sanity-check.sh

docker-start:
	@./scripts/docker-start.sh

docker-stop:
	@./scripts/docker-stop.sh

docker-restart:
	@./scripts/docker-restart.sh

docker-status:
	@./scripts/docker-status.sh

docker-logs:
	@./scripts/docker-logs.sh

docker-tunnel-info:
	@./scripts/docker-tunnel-info.sh

docker-check:
	@./scripts/docker-check.sh

docker-aws-preflight:
	@./scripts/docker-aws-preflight.sh

docker-go:
	@./scripts/docker-go.sh

docker-verify:
	@./scripts/docker-verify.sh

docker-scan:
	@./scripts/docker-scan.sh

docker-full-check:
	@./scripts/docker-full-check.sh

dependency-freshness:
	@./agents/dependency-freshness.sh

release-check:
	@./scripts/release-check.sh

release-check-docker:
	@RELEASE_CHECK_DOCKER=true ./scripts/release-check.sh

prepare-release:
	@test -n "$(VERSION)" || (echo "usage: make prepare-release VERSION=v0.2.0" >&2 && exit 1)
	@./scripts/prepare-release.sh "$(VERSION)"

clean-ds-store:
	@find . -path ./.git -prune -o -name .DS_Store -type f -exec rm -f {} +

test: test-ops test-backend test-frontend

verify: test build-frontend test-mcp build-mcp test-scripts

test-ops:
	@printf '%s\n' 'running: ./ops/tests/test-root-layout.sh'
	@bash ./ops/tests/test-root-layout.sh
	@printf '%s\n' 'running: ./ops/tests/test-doc-privacy.sh'
	@bash ./ops/tests/test-doc-privacy.sh
	@printf '%s\n' 'running: ./ops/tests/test-local-verify.sh'
	@bash ./ops/tests/test-local-verify.sh
	@printf '%s\n' 'running: ./ops/tests/test-start.sh'
	@bash ./ops/tests/test-start.sh
	@printf '%s\n' 'running: ./ops/tests/test-restart.sh'
	@bash ./ops/tests/test-restart.sh
	@printf '%s\n' 'running: ./ops/tests/test-start-backend-helper.sh'
	@bash ./ops/tests/test-start-backend-helper.sh
	@printf '%s\n' 'running: ./ops/tests/test-build.sh'
	@bash ./ops/tests/test-build.sh
	@printf '%s\n' 'running: ./ops/tests/test-status.sh'
	@bash ./ops/tests/test-status.sh
	@printf '%s\n' 'running: ./ops/tests/test-docker-lifecycle.sh'
	@bash ./ops/tests/test-docker-lifecycle.sh
	@printf '%s\n' 'running: ./ops/tests/test-docker-scan.sh'
	@bash ./ops/tests/test-docker-scan.sh
	@printf '%s\n' 'running: ./ops/tests/test-release-check.sh'
	@bash ./ops/tests/test-release-check.sh
	@printf '%s\n' 'running: ./ops/tests/test-prepare-release.sh'
	@bash ./ops/tests/test-prepare-release.sh
	@printf '%s\n' 'running: ./ops/tests/test-stop.sh'
	@bash ./ops/tests/test-stop.sh

test-backend:
	@cd backend && mvn test

test-frontend:
	@cd frontend && npm test -- --run

test-rag-qdrant-smoke:
	@bash ./ops/tests/test-rag-qdrant-smoke.sh

build-frontend:
	@cd frontend && npm run build

test-mcp:
	@cd mcp && npm test

build-mcp:
	@cd mcp && npm run build

test-scripts:
	@cd agents && make lint && make test
