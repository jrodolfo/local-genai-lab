SHELL := /bin/bash

.PHONY: help start stop restart status test-backend test-frontend

help:
	@printf '%s\n' \
		'Available targets:' \
		'  make start         Start backend and frontend in the background' \
		'  make stop          Stop background backend and frontend processes' \
		'  make restart       Restart backend and frontend' \
		'  make status        Show process, URL, and log status' \
		'  make test-backend  Run backend tests' \
		'  make test-frontend Run frontend tests'

start:
	@./start.sh

stop:
	@./stop.sh

restart:
	@./restart.sh

status:
	@./status.sh

test-backend:
	@cd backend && mvn test

test-frontend:
	@cd frontend && npm test -- --run
