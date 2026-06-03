#!/usr/bin/env bash
#
# start.sh
#
# Purpose:
#   Starts the local-genai-lab application (backend and frontend) in the
#   background. It handles environment loading, dependency checks (frontend),
#   and health checks to ensure the application starts correctly.
#
# Usage:
#   ./start.sh
#
# Required Tools:
#   - bash
#   - npm (for frontend dependencies and dev server)
#   - mvn (via start-backend-helper.sh)
#   - curl (for health checks)
#
# Expected Output:
#   Startup progress messages, runtime configuration header, and a success or
#   failure message indicating whether the application is ready.
#
# Exit Behavior:
#   Exits with 0 on success, 1 on failure (e.g., port already in use, health
#   check timeout).
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
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-60}"

# --- Checks ---
printf '%s\n' 'Starting local-genai-lab'
print_runtime_header
printf '%s\n' \
  "backend_url=${BACKEND_URL}" \
  "frontend_url=${FRONTEND_URL}" \
  "backend_log=${BACKEND_LOG_FILE}" \
  "frontend_log=${FRONTEND_LOG_FILE}"

# Verify if processes are already running
backend_pid="$(read_pid_file "${BACKEND_PID_FILE}")"
if [ -n "${backend_pid}" ] && is_process_alive "${backend_pid}"; then
  printf '%s\n' "Backend already running (pid=${backend_pid})." >&2
  exit 1
fi

frontend_pid="$(read_pid_file "${FRONTEND_PID_FILE}")"
if [ -n "${frontend_pid}" ] && is_process_alive "${frontend_pid}"; then
  printf '%s\n' "Frontend already running (pid=${frontend_pid})." >&2
  exit 1
fi

# Verify port availability
backend_port_owner="$(find_port_process "${SERVER_PORT}")"
if [ -n "${backend_port_owner}" ]; then
  printf '%s\n' \
    "Error: backend port ${SERVER_PORT} is already in use by pid ${backend_port_owner}." \
    "Use SERVER_PORT=<port> ./start.sh or stop the existing process first." >&2
  exit 1
fi

frontend_port_owner="$(find_port_process "${FRONTEND_PORT}")"
if [ -n "${frontend_port_owner}" ]; then
  printf '%s\n' \
    "Error: frontend port ${FRONTEND_PORT} is already in use by pid ${frontend_port_owner}." \
    "Use FRONTEND_PORT=<port> ./start.sh or stop the existing process first." >&2
  exit 1
fi

# --- Dependencies ---
if [ ! -d "${REPO_ROOT}/frontend/node_modules" ]; then
  printf '%s\n' 'Installing frontend dependencies because frontend/node_modules is missing...'
  (
    cd "${REPO_ROOT}/frontend"
    npm install
  )
fi

# --- Helpers ---
start_detached_process() {
  local log_file="$1"
  shift

  if command -v python3 >/dev/null 2>&1; then
    python3 - "${log_file}" "$@" <<'PY'
import os
import subprocess
import sys

log_file = sys.argv[1]
command = sys.argv[2:]

with open(log_file, "ab", buffering=0) as log:
    process = subprocess.Popen(
        command,
        stdin=subprocess.DEVNULL,
        stdout=log,
        stderr=subprocess.STDOUT,
        close_fds=True,
        start_new_session=True,
    )

print(process.pid)
PY
    return 0
  fi

  nohup "$@" >> "${log_file}" 2>&1 &
  printf '%s\n' "$!"
}

# --- Execution ---
: > "${BACKEND_LOG_FILE}"
: > "${FRONTEND_LOG_FILE}"

# Start Backend
backend_pid="$(start_detached_process "${BACKEND_LOG_FILE}" bash -c '
  set -euo pipefail
  repo_root="$1"
  server_port="$2"
  cd "${repo_root}"
  export SERVER_PORT="${server_port}"
  exec bash "${repo_root}/ops/start-backend-helper.sh"
' _ "${REPO_ROOT}" "${SERVER_PORT}")"
printf '%s' "${backend_pid}" > "${BACKEND_PID_FILE}"

# Start Frontend
frontend_pid="$(start_detached_process "${FRONTEND_LOG_FILE}" bash -c '
  set -euo pipefail
  repo_root="$1"
  backend_url="$2"
  frontend_port="$3"
  cd "${repo_root}/frontend"
  export BACKEND_URL="${backend_url}"
  export FRONTEND_PORT="${frontend_port}"
  exec npm run dev -- --host 0.0.0.0 --port "${FRONTEND_PORT}"
' _ "${REPO_ROOT}" "${BACKEND_URL}" "${FRONTEND_PORT}")"
printf '%s' "${frontend_pid}" > "${FRONTEND_PID_FILE}"

# --- Health Checks ---
if ! wait_for_url "${BACKEND_URL}/actuator/health" "${WAIT_TIMEOUT_SECONDS}"; then
  terminate_pid "${frontend_pid}" "frontend" || true
  terminate_pid "${backend_pid}" "backend" || true
  rm -f "${FRONTEND_PID_FILE}" "${BACKEND_PID_FILE}"
  printf '%s\n' \
    "Backend did not become healthy within ${WAIT_TIMEOUT_SECONDS}s." \
    "See ${BACKEND_LOG_FILE} for details." >&2
  exit 1
fi

if ! wait_for_url "${FRONTEND_URL}" "${WAIT_TIMEOUT_SECONDS}"; then
  terminate_pid "${frontend_pid}" "frontend" || true
  terminate_pid "${backend_pid}" "backend" || true
  rm -f "${FRONTEND_PID_FILE}" "${BACKEND_PID_FILE}"
  printf '%s\n' \
    "Frontend did not become ready within ${WAIT_TIMEOUT_SECONDS}s." \
    "See ${FRONTEND_LOG_FILE} for details." >&2
  exit 1
fi

printf '%s\n' \
  "Started local-genai-lab successfully." \
  "  backend pid=${backend_pid} url=${BACKEND_URL}" \
  "  frontend pid=${frontend_pid} url=${FRONTEND_URL}"
