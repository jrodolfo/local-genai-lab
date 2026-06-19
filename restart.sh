#!/usr/bin/env bash
#
# restart.sh
#
# Purpose:
#   Restarts the local-genai-lab application by stopping managed processes and
#   configured port owners, then starting it again.
#
# Usage:
#   ./restart.sh
#
# Important Environment:
#   Pass the same variables accepted by start.sh, for example:
#   RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh
#
# Required Tools:
#   - bash
#
# Expected Output:
#   Status messages from stop.sh and start.sh.
#
# Exit Behavior:
#   Exits with the status of the start.sh command.
#

set -euo pipefail

# --- Initialization ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ops/lib/runtime-common.sh
source "${SCRIPT_DIR}/ops/lib/runtime-common.sh"

load_env_defaults "${ENV_FILE}"
ensure_run_dir

# --- Configuration ---
SERVER_PORT="${SERVER_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_URL="${BACKEND_URL:-http://localhost:${SERVER_PORT}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT}}"

# --- Helpers ---
print_restart_failure() {
  local reason="$1"
  printf '%s\n' \
    'restart failed' \
    "reason: ${reason}" \
    ''
  print_runtime_endpoints_and_logs "${BACKEND_URL}" "${FRONTEND_URL}"
}

blocking_port_summary() {
  local frontend_owner backend_owner

  frontend_owner="$(find_port_process "${FRONTEND_PORT}")"
  if [ -n "${frontend_owner}" ]; then
    printf 'frontend port %s is still owned by pid %s\nnext step: %s' \
      "${FRONTEND_PORT}" \
      "${frontend_owner}" \
      "$(kill_command_hint "${frontend_owner}")"
    return 0
  fi

  backend_owner="$(find_port_process "${SERVER_PORT}")"
  if [ -n "${backend_owner}" ]; then
    printf 'backend port %s is still owned by pid %s\nnext step: %s' \
      "${SERVER_PORT}" \
      "${backend_owner}" \
      "$(kill_command_hint "${backend_owner}")"
    return 0
  fi

  printf 'stop step failed before startup'
}

# --- Execution ---
stop_status=0
bash "${SCRIPT_DIR}/stop.sh" --all 2>&1 || stop_status=$?
if [ "${stop_status}" -ne 0 ]; then
  print_restart_failure "$(blocking_port_summary)"
  exit "${stop_status}"
fi

start_status=0
bash "${SCRIPT_DIR}/start.sh" || start_status=$?
if [ "${start_status}" -ne 0 ]; then
  print_restart_failure "startup failed; check the logs below"
  exit "${start_status}"
fi

printf '%s\n' \
  'restart completed' \
  ''
print_runtime_endpoints_and_logs "${BACKEND_URL}" "${FRONTEND_URL}"
