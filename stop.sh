#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/lib/runtime-common.sh
source "${SCRIPT_DIR}/scripts/lib/runtime-common.sh"

ensure_run_dir
clear_stale_pid_file "${BACKEND_PID_FILE}"
clear_stale_pid_file "${FRONTEND_PID_FILE}"

backend_pid="$(read_pid_file "${BACKEND_PID_FILE}")"
frontend_pid="$(read_pid_file "${FRONTEND_PID_FILE}")"

stopped_any=false

if [ -n "${frontend_pid}" ]; then
  terminate_pid "${frontend_pid}" "frontend"
  rm -f "${FRONTEND_PID_FILE}"
  printf '%s\n' "Stopped frontend (pid=${frontend_pid})."
  stopped_any=true
fi

if [ -n "${backend_pid}" ]; then
  terminate_pid "${backend_pid}" "backend"
  rm -f "${BACKEND_PID_FILE}"
  printf '%s\n' "Stopped backend (pid=${backend_pid})."
  stopped_any=true
fi

if [ "${stopped_any}" = false ]; then
  printf '%s\n' 'No managed local-genai-lab processes were running.'
fi
