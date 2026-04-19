#!/usr/bin/env bash
# Starts the Spring Boot backend in Hugging Face mode with curated hosted-model defaults.
#
# Override behavior with environment variables such as:
#   HUGGINGFACE_API_TOKEN=hf_xxx
#   HUGGINGFACE_DEFAULT_MODEL=meta-llama/Llama-3.1-8B-Instruct
#   HUGGINGFACE_MODELS=meta-llama/Llama-3.1-8B-Instruct,Qwen/Qwen2.5-72B-Instruct
#   MCP_ENABLED=false
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

HUGGINGFACE_BASE_URL="${HUGGINGFACE_BASE_URL:-https://router.huggingface.co/v1/chat/completions}"
HUGGINGFACE_DEFAULT_MODEL="${HUGGINGFACE_DEFAULT_MODEL:-meta-llama/Llama-3.1-8B-Instruct}"
HUGGINGFACE_MODELS="${HUGGINGFACE_MODELS:-${HUGGINGFACE_DEFAULT_MODEL}}"
HUGGINGFACE_API_TOKEN="${HUGGINGFACE_API_TOKEN:-}"
MCP_ENABLED="${MCP_ENABLED:-true}"

if [ -z "${HUGGINGFACE_API_TOKEN}" ]; then
  printf '%s\n' 'Error: HUGGINGFACE_API_TOKEN is required for the Hugging Face provider.' >&2
  exit 1
fi

printf '%s\n' \
  "Starting backend with provider=huggingface" \
  "  env_file=$([ -f "${ENV_FILE}" ] && printf '%s' '.env loaded' || printf '%s' 'none')" \
  "  base_url=${HUGGINGFACE_BASE_URL}" \
  "  default_model=${HUGGINGFACE_DEFAULT_MODEL}" \
  "  models=${HUGGINGFACE_MODELS}" \
  "  token_configured=$([ -n "${HUGGINGFACE_API_TOKEN}" ] && printf '%s' 'true' || printf '%s' 'false')" \
  "  mcp_enabled=${MCP_ENABLED}"

cd "${BACKEND_DIR}"

mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dapp.model.provider=huggingface -Dhuggingface.base-url=${HUGGINGFACE_BASE_URL} -Dhuggingface.api-token=${HUGGINGFACE_API_TOKEN} -Dhuggingface.default-model=${HUGGINGFACE_DEFAULT_MODEL} -Dhuggingface.models=${HUGGINGFACE_MODELS} -Dmcp.enabled=${MCP_ENABLED}"
