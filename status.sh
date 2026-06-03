#!/usr/bin/env bash
#
# status.sh
#
# Purpose:
#   Displays the current status of the local-genai-lab application, including
#   process PIDs, URLs, health check results, and log file locations.
#
# Usage:
#   ./status.sh
#
# Required Tools:
#   - bash
#   - curl (for health checks)
#   - ollama (optional; for local provider/RAG readiness checks)
#
# Expected Output:
#   Status summary for backend and frontend, health check results, and log
#   file paths.
#
# Exit Behavior:
#   Exits with 0.
#

set -euo pipefail

# --- Initialization ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ops/lib/runtime-common.sh
source "${SCRIPT_DIR}/ops/lib/runtime-common.sh"

load_env_defaults "${ENV_FILE}"
ensure_run_dir
clear_stale_pid_file "${BACKEND_PID_FILE}"
clear_stale_pid_file "${FRONTEND_PID_FILE}"

# --- Configuration ---
SERVER_PORT="${SERVER_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_URL="${BACKEND_URL:-http://localhost:${SERVER_PORT}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT}}"
APP_MODEL_PROVIDER="${APP_MODEL_PROVIDER:-ollama}"
RAG_ENABLED="${RAG_ENABLED:-true}"
RAG_RETRIEVAL_MODE="${RAG_RETRIEVAL_MODE:-lexical}"
RAG_VECTOR_STORE="${RAG_VECTOR_STORE:-in-memory}"
RAG_QDRANT_URL="${RAG_QDRANT_URL:-http://localhost:6333}"
RAG_QDRANT_COLLECTION="${RAG_QDRANT_COLLECTION:-local_genai_lab_docs}"
RAG_EMBEDDING_PROVIDER="${RAG_EMBEDDING_PROVIDER:-ollama}"
RAG_EMBEDDING_MODEL="${RAG_EMBEDDING_MODEL:-nomic-embed-text}"

normalize_bool() {
  local value
  value="$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')"
  case "${value}" in
    true | 1 | yes | y | on)
      printf '%s' 'true'
      ;;
    *)
      printf '%s' 'false'
      ;;
  esac
}

normalize_lower() {
  printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]'
}

ollama_model_installed() {
  local model_name="$1"
  ollama list 2>/dev/null | awk 'NR > 1 {print $1}' | grep -Fxq "${model_name}"
}

# --- Process Status ---
backend_pid="$(read_pid_file "${BACKEND_PID_FILE}")"
frontend_pid="$(read_pid_file "${FRONTEND_PID_FILE}")"

printf '%s\n' 'local-genai-lab status'
print_runtime_header

if [ -n "${backend_pid}" ] && is_process_alive "${backend_pid}"; then
  printf '%s\n' "backend: running (pid=${backend_pid}, url=${BACKEND_URL})"
else
  backend_port_owner="$(find_port_process "${SERVER_PORT}")"
  if [ -n "${backend_port_owner}" ]; then
    printf '%s\n' "backend: unmanaged process on port ${SERVER_PORT} (pid=${backend_port_owner}, url=${BACKEND_URL})"
  else
    printf '%s\n' "backend: stopped (url=${BACKEND_URL})"
  fi
fi

if [ -n "${frontend_pid}" ] && is_process_alive "${frontend_pid}"; then
  printf '%s\n' "frontend: running (pid=${frontend_pid}, url=${FRONTEND_URL})"
else
  frontend_port_owner="$(find_port_process "${FRONTEND_PORT}")"
  if [ -n "${frontend_port_owner}" ]; then
    printf '%s\n' "frontend: unmanaged process on port ${FRONTEND_PORT} (pid=${frontend_port_owner}, url=${FRONTEND_URL})"
  else
    printf '%s\n' "frontend: stopped (url=${FRONTEND_URL})"
  fi
fi

# --- Health Checks ---
if curl -fsS "${BACKEND_URL}/actuator/health" >/dev/null 2>&1; then
  printf '%s\n' 'backend health: ok'
else
  printf '%s\n' 'backend health: unavailable'
fi

if curl -fsS "${FRONTEND_URL}" >/dev/null 2>&1; then
  printf '%s\n' 'frontend http: ok'
else
  printf '%s\n' 'frontend http: unavailable'
fi

# --- RAG and Ollama Readiness ---
rag_enabled_normalized="$(normalize_bool "${RAG_ENABLED}")"
rag_retrieval_mode_normalized="$(normalize_lower "${RAG_RETRIEVAL_MODE}")"
rag_vector_store_normalized="$(normalize_lower "${RAG_VECTOR_STORE}")"
app_model_provider_normalized="$(normalize_lower "${APP_MODEL_PROVIDER}")"
rag_embedding_provider_normalized="$(normalize_lower "${RAG_EMBEDDING_PROVIDER}")"
needs_ollama_status='false'
needs_embedding_model='false'

if [ "${app_model_provider_normalized}" = 'ollama' ]; then
  needs_ollama_status='true'
fi

if [ "${rag_enabled_normalized}" = 'true' ] \
  && [ "${rag_retrieval_mode_normalized}" = 'vector' ] \
  && [ "${rag_embedding_provider_normalized}" = 'ollama' ]; then
  needs_ollama_status='true'
  needs_embedding_model='true'
fi

printf '%s\n' \
  "rag enabled: ${rag_enabled_normalized}" \
  "rag retrieval mode: ${rag_retrieval_mode_normalized}" \
  "rag vector store: ${rag_vector_store_normalized}" \
  "rag embedding provider: ${RAG_EMBEDDING_PROVIDER}" \
  "rag embedding model: ${RAG_EMBEDDING_MODEL}"

if [ "${rag_enabled_normalized}" = 'true' ] \
  && [ "${rag_retrieval_mode_normalized}" = 'vector' ] \
  && [ "${rag_vector_store_normalized}" = 'qdrant' ]; then
  printf '%s\n' \
    "rag qdrant url: ${RAG_QDRANT_URL}" \
    "rag qdrant collection: ${RAG_QDRANT_COLLECTION}" \
    'qdrant service: not checked'
fi

if [ "${needs_ollama_status}" = 'true' ]; then
  if command -v ollama >/dev/null 2>&1; then
    printf '%s\n' 'ollama cli: available'
    if curl -fsS "${OLLAMA_BASE_URL:-http://localhost:11434}/api/tags" >/dev/null 2>&1; then
      printf '%s\n' 'ollama service: ok'
      if [ "${needs_embedding_model}" = 'true' ]; then
        if ollama_model_installed "${RAG_EMBEDDING_MODEL}"; then
          printf '%s\n' "ollama embedding model: present (${RAG_EMBEDDING_MODEL})"
        else
          printf '%s\n' "ollama embedding model: missing (${RAG_EMBEDDING_MODEL})"
          printf '%s\n' "hint: run ollama pull ${RAG_EMBEDDING_MODEL}"
        fi
      else
        printf '%s\n' 'ollama embedding model: not required for current mode'
      fi
    else
      printf '%s\n' "ollama service: unavailable (${OLLAMA_BASE_URL:-http://localhost:11434})"
      if [ "${needs_embedding_model}" = 'true' ]; then
        printf '%s\n' "ollama embedding model: not checked (${RAG_EMBEDDING_MODEL})"
      fi
    fi
  else
    printf '%s\n' 'ollama cli: missing'
    printf '%s\n' 'ollama service: not checked'
    if [ "${needs_embedding_model}" = 'true' ]; then
      printf '%s\n' "ollama embedding model: not checked (${RAG_EMBEDDING_MODEL})"
    fi
  fi
else
  printf '%s\n' 'ollama readiness: not required for current configuration'
fi

# --- Logs ---
printf '%s\n' \
  "backend log: ${BACKEND_LOG_FILE}" \
  "frontend log: ${FRONTEND_LOG_FILE}"
