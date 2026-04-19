#!/usr/bin/env bash
# Starts the Spring Boot backend in Bedrock mode with the preferred local defaults.
#
# Override behavior with environment variables such as:
#   BEDROCK_REGION=us-east-2
#   BEDROCK_MODEL_ID=us.amazon.nova-pro-v1:0
#   MCP_ENABLED=false
# AWS credentials are resolved by the standard AWS SDK chain used by the backend.
# If a repo-local .env file exists, it is loaded for unset variables only.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"
ENV_FILE="${REPO_ROOT}/.env"

load_env_defaults() {
  local env_file="$1"
  local line key value

  [ -f "${env_file}" ] || return 0

  while IFS= read -r line || [ -n "${line}" ]; do
    line="${line%$'\r'}"
    if [[ -z "${line}" || "${line}" == \#* ]]; then
      continue
    fi
    key="${line%%=*}"
    value="${line#*=}"
    if [[ -z "${key}" || "${key}" == "${line}" ]]; then
      continue
    fi
    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi
    if [ -n "${!key+x}" ]; then
      continue
    fi
    if [[ "${value}" =~ ^\".*\"$ || "${value}" =~ ^\'.*\'$ ]]; then
      value="${value:1:${#value}-2}"
    fi
    export "${key}=${value}"
  done < "${env_file}"
}

load_env_defaults "${ENV_FILE}"

BEDROCK_REGION="${BEDROCK_REGION:-us-east-2}"
BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-us.amazon.nova-pro-v1:0}"
MCP_ENABLED="${MCP_ENABLED:-true}"

printf '%s\n' \
  "Starting backend with provider=bedrock" \
  "  env_file=$([ -f "${ENV_FILE}" ] && printf '%s' '.env loaded' || printf '%s' 'none')" \
  "  region=${BEDROCK_REGION}" \
  "  model=${BEDROCK_MODEL_ID}" \
  "  mcp_enabled=${MCP_ENABLED}"

cd "${BACKEND_DIR}"

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dapp.model.provider=bedrock -Dbedrock.region=${BEDROCK_REGION} -Dbedrock.model-id=${BEDROCK_MODEL_ID} -Dmcp.enabled=${MCP_ENABLED}"
