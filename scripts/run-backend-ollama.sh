#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"

MCP_ENABLED="${MCP_ENABLED:-true}"

printf '%s\n' \
  "Starting backend with provider=ollama" \
  "  mcp_enabled=${MCP_ENABLED}"

cd "${BACKEND_DIR}"

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dapp.model.provider=ollama -Dmcp.enabled=${MCP_ENABLED}"
