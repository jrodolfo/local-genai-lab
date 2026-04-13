SHELL := /bin/bash

SCRIPT := ./aws-region-audit-report.sh
S3_CLOUDWATCH_SCRIPT := ./aws-s3-cloudwatch-report.sh
REPORTS_DIR := reports
REGIONS ?=
SERVICES ?=
BUCKET ?=
REGION ?=
DAYS ?=

.PHONY: help reports lint test audit s3-cloudwatch check-app run-backend-ollama run-backend-bedrock

help:
	@printf '%s\n' \
		'Available targets:' \
		'  make help     Show this help text' \
		'  make reports  Create the reports directory' \
		'  make lint     Check Bash syntax for the shell scripts' \
		'  make test     Run local shell tests without AWS access' \
		'  make check-app  Verify backend, frontend, and optional Ollama health' \
		'  make run-backend-ollama  Start backend with the Ollama provider' \
		'  make run-backend-bedrock Start backend with Bedrock provider' \
		'                Optional: BEDROCK_REGION=us-east-2 BEDROCK_MODEL_ID=us.amazon.nova-pro-v1:0' \
		'  make audit    Run the AWS regional audit report' \
		'                Optional: make audit REGIONS="us-east-2"' \
		'                Optional: make audit SERVICES="sagemaker ec2"' \
		'  make s3-cloudwatch BUCKET=example.com' \
		'                Optional: make s3-cloudwatch BUCKET=example.com REGION=us-east-2' \
		'                Optional: make s3-cloudwatch BUCKET=example.com DAYS=30' \
		'  Optional env for make check-app:' \
		'                BACKEND_URL=http://localhost:8080' \
		'                FRONTEND_URL=http://localhost:5173' \
		'                OLLAMA_URL=http://localhost:11434' \
		'                CHECK_OLLAMA=true'

reports:
	@mkdir -p "$(REPORTS_DIR)"

lint:
	@bash -n "$(SCRIPT)"
	@bash -n "$(S3_CLOUDWATCH_SCRIPT)"
	@bash -n ./check-app.sh
	@bash -n ./run-backend-ollama.sh
	@bash -n ./run-backend-bedrock.sh

test:
	@bash ./tests/test.sh
	@bash ./tests/test-s3-cloudwatch.sh

check-app:
	@./check-app.sh

run-backend-ollama:
	@./run-backend-ollama.sh

run-backend-bedrock:
	@./run-backend-bedrock.sh

audit: reports lint
	@"$(SCRIPT)" \
		$(if $(strip $(REGIONS)),--regions $(REGIONS),) \
		$(if $(strip $(SERVICES)),--services $(SERVICES),)

s3-cloudwatch: reports lint
	@if [ -z "$(strip $(BUCKET))" ]; then \
		printf '%s\n' 'Error: BUCKET is required. Example: make s3-cloudwatch BUCKET=example.com' >&2; \
		exit 1; \
	fi
	@"$(S3_CLOUDWATCH_SCRIPT)" \
		--bucket "$(BUCKET)" \
		$(if $(strip $(REGION)),--region $(REGION),) \
		$(if $(strip $(DAYS)),--days $(DAYS),)
