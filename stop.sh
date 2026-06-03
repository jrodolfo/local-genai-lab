#!/usr/bin/env bash
#
# stop.sh
#
# Purpose:
#   Stops the local-genai-lab application by terminating the backend and
#   frontend processes tracked by PID files. With --all, also terminates
#   processes currently listening on the configured backend/frontend ports.
#
# Usage:
#   ./stop.sh
#   ./stop.sh --all
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

load_env_defaults "${ENV_FILE}"
ensure_run_dir
clear_stale_pid_file "${BACKEND_PID_FILE}"
clear_stale_pid_file "${FRONTEND_PID_FILE}"

# --- Arguments ---
STOP_PORT_OWNERS=false

while [ "$#" -gt 0 ]; do
  case "$1" in
    --all)
      STOP_PORT_OWNERS=true
      ;;
    *)
      printf '%s\n' "Unknown option: $1" >&2
      printf '%s\n' 'Usage: ./stop.sh [--all]' >&2
      exit 1
      ;;
  esac
  shift
done

# --- Configuration ---
SERVER_PORT="${SERVER_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

# --- Helpers ---
stop_unmanaged_port_owner() {
  local port="$1"
  local name="$2"
  local managed_pid="$3"
  local owner_pid

  owner_pid="$(find_port_process "${port}")"
  if [ -z "${owner_pid}" ]; then
    return 0
  fi

  if [ -n "${managed_pid}" ] && [ "${owner_pid}" = "${managed_pid}" ]; then
    return 0
  fi

  terminate_pid "${owner_pid}" "${name} port ${port}"
  printf '%s\n' "Stopped unmanaged ${name} port owner (pid=${owner_pid}, port=${port})."
  stopped_any=true
}

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

if [ "${STOP_PORT_OWNERS}" = true ]; then
  stop_unmanaged_port_owner "${FRONTEND_PORT}" "frontend" "${frontend_pid}"
  stop_unmanaged_port_owner "${SERVER_PORT}" "backend" "${backend_pid}"
fi

if [ "${stopped_any}" = false ]; then
  if [ "${STOP_PORT_OWNERS}" = true ]; then
    printf '%s\n' 'No managed local-genai-lab processes or configured port owners were running.'
  else
    printf '%s\n' 'No managed local-genai-lab processes were running.'
  fi
fi
