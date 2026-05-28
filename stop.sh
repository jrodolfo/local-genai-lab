#!/usr/bin/env bash
#
# stop.sh
#
# Purpose:
#   Stops the local-genai-lab application by terminating the backend and
#   frontend processes.
#
# Usage:
#   ./stop.sh
#
# Required Tools:
#   - bash
#   - kill (via terminate_pid in runtime-common.sh)
#
# Expected Output:
#   Status messages indicating which processes were stopped, or a message if
#   no managed processes were running.
#
# Exit Behavior:
#   Exits with 0.
#

set -euo pipefail

# --- Initialization ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ops/lib/runtime-common.sh
source "${SCRIPT_DIR}/ops/lib/runtime-common.sh"

ensure_run_dir
clear_stale_pid_file "${BACKEND_PID_FILE}"
clear_stale_pid_file "${FRONTEND_PID_FILE}"

# --- Execution ---
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
