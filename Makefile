SHELL := /bin/bash

.PHONY: help start stop restart status build check-app docker-start docker-stop docker-restart docker-status docker-check clean-ds-store test verify test-ops test-backend test-frontend test-rag-qdrant-smoke build-frontend test-mcp build-mcp test-scripts

help:
	@printf '%s\n' \
		'Available targets:' \
		'  make start                  Start backend and frontend in the background' \
		'  make stop                   Stop background backend and frontend processes' \
		'  make restart                Restart backend and frontend' \
		'  make status                 Show process, URL, and log status' \
		'  make build                  Build backend, frontend, and MCP artifacts' \
		'  make check-app              Run the local stack smoke check' \
		'  make docker-start           Start backend, frontend, and Qdrant with Docker Compose' \
		'  make docker-stop            Stop the Docker Compose stack' \
		'  make docker-restart         Restart the Docker Compose stack' \
		'  make docker-status          Show Docker Compose service status' \
		'  make docker-check           Smoke-check the running Docker Compose stack' \
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
	@./start.sh

stop:
	@./stop.sh

restart:
	@./restart.sh

status:
	@./status.sh

build:
	@./build.sh

check-app:
	@./ops/check-app.sh

docker-start:
	@./docker-start.sh

docker-stop:
	@./docker-stop.sh

docker-restart:
	@./docker-restart.sh

docker-status:
	@./docker-status.sh

docker-check:
	@./docker-check.sh

clean-ds-store:
	@find . -path ./.git -prune -o -name .DS_Store -type f -exec rm -f {} +

test: test-ops test-backend test-frontend

verify: test build-frontend test-mcp build-mcp test-scripts

test-ops:
	@bash ./ops/tests/test-doc-privacy.sh
	@bash ./ops/tests/test-start.sh
	@bash ./ops/tests/test-restart.sh
	@bash ./ops/tests/test-start-backend-helper.sh
	@bash ./ops/tests/test-build.sh
	@bash ./ops/tests/test-status.sh
	@bash ./ops/tests/test-docker-lifecycle.sh
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
	@cd scripts && make lint && make test
