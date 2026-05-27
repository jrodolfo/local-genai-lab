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

printf '%s\n' \
  "backend log: ${BACKEND_LOG_FILE}" \
  "frontend log: ${FRONTEND_LOG_FILE}"
