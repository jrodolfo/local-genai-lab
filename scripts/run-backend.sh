#!/usr/bin/env bash
# Starts the Spring Boot backend with the providers configured for the current local environment.
#
# The script auto-loads the repo-local .env file for unset variables only, so explicit shell
# overrides still win. Use APP_MODEL_PROVIDER to choose the startup default provider while keeping
# any other configured providers available for runtime switching in the UI.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${REPO_ROOT}/backend"
ENV_FILE="${ENV_FILE:-${REPO_ROOT}/.env}"

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

APP_MODEL_PROVIDER="${APP_MODEL_PROVIDER:-ollama}"
OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
OLLAMA_DEFAULT_MODEL="${OLLAMA_DEFAULT_MODEL:-llama3:8b}"
BEDROCK_REGION="${BEDROCK_REGION:-us-east-2}"
BEDROCK_MODEL_ID="${BEDROCK_MODEL_ID:-us.amazon.nova-pro-v1:0}"
HUGGINGFACE_BASE_URL="${HUGGINGFACE_BASE_URL:-https://router.huggingface.co/v1/chat/completions}"
HUGGINGFACE_DEFAULT_MODEL="${HUGGINGFACE_DEFAULT_MODEL:-meta-llama/Llama-3.1-8B-Instruct}"
HUGGINGFACE_MODELS="${HUGGINGFACE_MODELS:-${HUGGINGFACE_DEFAULT_MODEL}}"
HUGGINGFACE_API_TOKEN="${HUGGINGFACE_API_TOKEN:-}"
MCP_ENABLED="${MCP_ENABLED:-true}"
DRY_RUN="${DRY_RUN:-false}"

export APP_MODEL_PROVIDER
export OLLAMA_BASE_URL
export OLLAMA_DEFAULT_MODEL
export BEDROCK_REGION
export BEDROCK_MODEL_ID
export HUGGINGFACE_BASE_URL
export HUGGINGFACE_DEFAULT_MODEL
export HUGGINGFACE_MODELS
export HUGGINGFACE_API_TOKEN
export MCP_ENABLED
export DRY_RUN

case "${APP_MODEL_PROVIDER}" in
  ollama|bedrock|huggingface)
    ;;
  *)
    printf '%s\n' "Error: APP_MODEL_PROVIDER must be one of: ollama, bedrock, huggingface." >&2
    exit 1
    ;;
esac

if [ "${APP_MODEL_PROVIDER}" = "huggingface" ] && [ -z "${HUGGINGFACE_API_TOKEN}" ]; then
  printf '%s\n' 'Error: HUGGINGFACE_API_TOKEN is required when APP_MODEL_PROVIDER=huggingface.' >&2
  exit 1
fi

printf '%s\n' \
  "Starting backend with provider=${APP_MODEL_PROVIDER}" \
  "  env_file=$([ -f "${ENV_FILE}" ] && printf '%s' '.env loaded' || printf '%s' 'none')" \
  "  ollama_base_url=${OLLAMA_BASE_URL}" \
  "  ollama_default_model=${OLLAMA_DEFAULT_MODEL}" \
  "  bedrock_region=${BEDROCK_REGION}" \
  "  bedrock_model=${BEDROCK_MODEL_ID}" \
  "  huggingface_base_url=${HUGGINGFACE_BASE_URL}" \
  "  huggingface_default_model=${HUGGINGFACE_DEFAULT_MODEL}" \
  "  huggingface_models=${HUGGINGFACE_MODELS}" \
  "  huggingface_token_configured=$([ -n "${HUGGINGFACE_API_TOKEN}" ] && printf '%s' 'true' || printf '%s' 'false')" \
  "  mcp_enabled=${MCP_ENABLED}"

if [ "${DRY_RUN}" = "true" ]; then
  exit 0
fi

cd "${BACKEND_DIR}"

exec mvn spring-boot:run
