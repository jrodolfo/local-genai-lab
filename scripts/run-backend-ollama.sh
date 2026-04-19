#!/usr/bin/env bash
# Starts the Spring Boot backend in Ollama mode using the repository's preferred local defaults.
#
# Override behavior with environment variables such as:
#   MCP_ENABLED=false
# The script keeps provider switching simple for daily local use.
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

MCP_ENABLED="${MCP_ENABLED:-true}"

printf '%s\n' \
  "Starting backend with provider=ollama" \
  "  env_file=$([ -f "${ENV_FILE}" ] && printf '%s' '.env loaded' || printf '%s' 'none')" \
  "  mcp_enabled=${MCP_ENABLED}"

cd "${BACKEND_DIR}"

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dapp.model.provider=ollama -Dmcp.enabled=${MCP_ENABLED}"
