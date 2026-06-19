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
# Important Environment:
#   SERVER_PORT / FRONTEND_PORT select which unmanaged port owners --all may stop.
#   RUN_DIR selects where PID files are read from.
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
wait_for_port_release() {
  local port="$1"
  local pid="$2"
  local name="$3"
  local waited=0
  local owner_pid
  local printed_dots='false'

  while [ "${waited}" -lt 15 ]; do
    owner_pid="$(find_port_process "${port}")"
    if [ -z "${owner_pid}" ] || [ "${owner_pid}" != "${pid}" ]; then
      if [ "${printed_dots}" = 'true' ]; then
        printf '\n'
      fi
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
    printf '.'
    printed_dots='true'
  done

  if [ "${printed_dots}" = 'true' ]; then
    printf '\n'
  fi
  printf '%s\n' \
    "${name} did not stop: port ${port} is still owned by pid ${pid}." \
    "next step: $(kill_command_hint "${pid}")" >&2
  return 1
}

stop_unmanaged_port_owner() {
  local port="$1"
  local name="$2"
  local managed_pid="$3"
  local owner_pid

  # --all exists for the common developer case where a previous run was started
  # outside the managed PID files but still owns the configured port.
  owner_pid="$(find_port_process "${port}")"
  if [ -z "${owner_pid}" ]; then
    return 0
  fi

  if [ -n "${managed_pid}" ] && [ "${owner_pid}" = "${managed_pid}" ]; then
    return 0
  fi

  printf '%s\n' "Stopping unmanaged ${name} port owner (pid=${owner_pid}, port=${port})..."
  terminate_pid "${owner_pid}" "${name} port ${port}"
  wait_for_port_release "${port}" "${owner_pid}" "${name}"
  printf '%s\n' "Stopped unmanaged ${name} port owner (pid=${owner_pid}, port=${port})."
  stopped_any=true
}

# --- Execution ---
backend_pid="$(read_pid_file "${BACKEND_PID_FILE}")"
frontend_pid="$(read_pid_file "${FRONTEND_PID_FILE}")"

stopped_any=false

if [ -n "${frontend_pid}" ]; then
  printf '%s\n' "Stopping frontend (pid=${frontend_pid})..."
  terminate_pid "${frontend_pid}" "frontend"
  wait_for_port_release "${FRONTEND_PORT}" "${frontend_pid}" "frontend"
  rm -f "${FRONTEND_PID_FILE}"
  printf '%s\n' "Stopped frontend (pid=${frontend_pid})."
  stopped_any=true
fi

if [ -n "${backend_pid}" ]; then
  printf '%s\n' "Stopping backend (pid=${backend_pid})..."
  terminate_pid "${backend_pid}" "backend"
  wait_for_port_release "${SERVER_PORT}" "${backend_pid}" "backend"
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
