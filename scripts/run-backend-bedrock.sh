#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

BEDROCK_REGION="${BEDROCK_REGION:-us-east-2}"
BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-us.amazon.nova-pro-v1:0}"
MCP_ENABLED="${MCP_ENABLED:-true}"

printf '%s\n' \
  "Starting backend with provider=bedrock" \
  "  region=${BEDROCK_REGION}" \
  "  model=${BEDROCK_MODEL_ID}" \
  "  mcp_enabled=${MCP_ENABLED}"

cd "${BACKEND_DIR}"

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dapp.model.provider=bedrock -Dbedrock.region=${BEDROCK_REGION} -Dbedrock.model-id=${BEDROCK_MODEL_ID} -Dmcp.enabled=${MCP_ENABLED}"
