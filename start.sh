#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ops/lib/runtime-common.sh
source "${SCRIPT_DIR}/ops/lib/runtime-common.sh"

load_env_defaults "${ENV_FILE}"
ensure_run_dir
clear_stale_pid_file "${BACKEND_PID_FILE}"
clear_stale_pid_file "${FRONTEND_PID_FILE}"

SERVER_PORT="${SERVER_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_URL="${BACKEND_URL:-http://localhost:${SERVER_PORT}}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:${FRONTEND_PORT}}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-60}"

printf '%s\n' 'Starting local-genai-lab'
print_runtime_header
printf '%s\n' \
  "backend_url=${BACKEND_URL}" \
  "frontend_url=${FRONTEND_URL}" \
  "backend_log=${BACKEND_LOG_FILE}" \
  "frontend_log=${FRONTEND_LOG_FILE}"

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

if [ ! -d "${REPO_ROOT}/frontend/node_modules" ]; then
  printf '%s\n' 'Installing frontend dependencies because frontend/node_modules is missing...'
  (
    cd "${REPO_ROOT}/frontend"
    npm install
  )
fi

: > "${BACKEND_LOG_FILE}"
: > "${FRONTEND_LOG_FILE}"

(
  cd "${REPO_ROOT}"
  export SERVER_PORT
  exec bash "${REPO_ROOT}/ops/start-backend-helper.sh"
) >> "${BACKEND_LOG_FILE}" 2>&1 &
backend_pid=$!
printf '%s' "${backend_pid}" > "${BACKEND_PID_FILE}"

(
  cd "${REPO_ROOT}/frontend"
  export BACKEND_URL
  export FRONTEND_PORT
  exec npm run dev -- --host 0.0.0.0 --port "${FRONTEND_PORT}"
) >> "${FRONTEND_LOG_FILE}" 2>&1 &
frontend_pid=$!
printf '%s' "${frontend_pid}" > "${FRONTEND_PID_FILE}"

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
