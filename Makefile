SHELL := /bin/bash

.PHONY: help start stop restart status check-app test verify test-ops test-backend test-frontend build-frontend test-mcp build-mcp test-scripts

help:
	@printf '%s\n' \
		'Available targets:' \
		'  make start         Start backend and frontend in the background' \
		'  make stop          Stop background backend and frontend processes' \
		'  make restart       Restart backend and frontend' \
		'  make status        Show process, URL, and log status' \
		'  make check-app     Run the local stack smoke check' \
		'  make test          Run ops, backend, and frontend tests' \
		'  make verify        Run broader project verification' \
		'  make test-ops      Run operational shell helper tests' \
		'  make test-backend  Run backend tests' \
		'  make test-frontend Run frontend tests' \
		'  make build-frontend Build the frontend' \
		'  make test-mcp      Run MCP tests' \
		'  make build-mcp     Build MCP server' \
		'  make test-scripts  Run MCP tool script lint/tests'

start:
	@./start.sh

stop:
	@./stop.sh

restart:
	@./restart.sh

status:
	@./status.sh

check-app:
	@./ops/check-app.sh

test: test-ops test-backend test-frontend

verify: test build-frontend test-mcp build-mcp test-scripts

test-ops:
	@bash ./ops/tests/test-start-backend-helper.sh
	@bash ./ops/tests/test-status.sh
	@bash ./ops/tests/test-stop.sh

test-backend:
	@cd backend && mvn test

test-frontend:
	@cd frontend && npm test -- --run

build-frontend:
	@cd frontend && npm run build

test-mcp:
	@cd mcp && npm test

build-mcp:
	@cd mcp && npm run build

test-scripts:
	@cd scripts && make lint && make test
