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
service_status_line() {
  local name="$1"
  local port="$2"
  local url="$3"
  local health_url="$4"
  local owner_pid

  owner_pid="$(find_port_process "${port}")"
  if [ -z "${owner_pid}" ]; then
    printf '  %s: not running at %s\n' "${name}" "${url}"
    return 0
  fi

  if curl_probe_url "${health_url}"; then
    printf '  %s: responding at %s (pid=%s)\n' "${name}" "${url}" "${owner_pid}"
  else
    printf '  %s: port %s is owned by pid %s, but %s is not responding\n' \
      "${name}" "${port}" "${owner_pid}" "${url}"
  fi
}

print_current_status() {
  printf '%s\n' 'current status:'
  service_status_line 'backend' "${SERVER_PORT}" "${BACKEND_URL}" "${BACKEND_URL}/actuator/health"
  service_status_line 'frontend' "${FRONTEND_PORT}" "${FRONTEND_URL}" "${FRONTEND_URL}"
  printf '%s\n' \
    '  mcp: backend-managed' \
    "    health: ${BACKEND_URL}/actuator/health" \
    "    log: ${BACKEND_LOG_FILE}"
}

print_log_paths() {
  printf '%s\n' \
    'logs:' \
    "  backend: ${BACKEND_LOG_FILE}" \
    "  frontend: ${FRONTEND_LOG_FILE}"
}

print_restart_stop_failure() {
  local component="$1"
  local port="$2"
  local pid="$3"

  printf '%s\n' \
    'restart did not start' \
    'phase: stopping existing services' \
    "blocked by: ${component}" \
    "reason: ${component} port ${port} is still owned by pid ${pid}" \
    ''
  print_current_status
  printf '%s\n' \
    '' \
    'next step:' \
    "  stop ${component} manually: $(kill_command_hint "${pid}")" \
    '  then retry: ./restart.sh' \
    ''
  print_log_paths
}

print_restart_start_failure() {
  printf '%s\n' \
    'restart did not complete' \
    'phase: starting services' \
    'blocked by: startup' \
    'reason: startup failed; check the logs below' \
    ''
  print_current_status
  printf '%s\n' ''
  print_log_paths
}

print_restart_success() {
  printf '%s\n' \
    'restart completed' \
    ''
  print_current_status
  printf '%s\n' ''
  print_log_paths
}

print_blocking_port_failure() {
  local frontend_owner backend_owner

  frontend_owner="$(find_port_process "${FRONTEND_PORT}")"
  if [ -n "${frontend_owner}" ]; then
    print_restart_stop_failure 'frontend' "${FRONTEND_PORT}" "${frontend_owner}"
    return 0
  fi

  backend_owner="$(find_port_process "${SERVER_PORT}")"
  if [ -n "${backend_owner}" ]; then
    print_restart_stop_failure 'backend' "${SERVER_PORT}" "${backend_owner}"
    return 0
  fi

  printf '%s\n' \
    'restart did not start' \
    'phase: stopping existing services' \
    'blocked by: unknown stop failure' \
    'reason: stop step failed before startup' \
    ''
  print_current_status
  printf '%s\n' ''
  print_log_paths
}

# --- Execution ---
stop_status=0
bash "${SCRIPT_DIR}/stop.sh" --all 2>&1 || stop_status=$?
if [ "${stop_status}" -ne 0 ]; then
  print_blocking_port_failure
  exit "${stop_status}"
fi

start_status=0
bash "${SCRIPT_DIR}/start.sh" || start_status=$?
if [ "${start_status}" -ne 0 ]; then
  print_restart_start_failure
  exit "${start_status}"
fi

print_restart_success
