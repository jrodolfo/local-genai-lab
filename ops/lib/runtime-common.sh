#!/usr/bin/env bash
#
# runtime-common.sh
#
# Purpose:
#   Provides shared utility functions and environment configuration for local
#   runtime management (backend/frontend).
#
# Usage:
#   source ops/lib/runtime-common.sh
#
# Required Tools:
#   - curl (for wait_for_url)
#   - lsof (for find_port_process)
#
# Expected Output:
#   None when sourced. Provides various output via functions.
#
# Exit Behavior:
#   No exit on its own. Functions return success (0) or failure (non-zero).
#

set -u

# --- Path and File Definitions ---
RUNTIME_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${RUNTIME_SCRIPT_DIR}/../.." && pwd)"
RUN_DIR="${RUN_DIR:-${REPO_ROOT}/.run}"
ENV_FILE="${ENV_FILE:-${REPO_ROOT}/.env}"

BACKEND_PID_FILE="${RUN_DIR}/backend.pid"
FRONTEND_PID_FILE="${RUN_DIR}/frontend.pid"
BACKEND_LOG_FILE="${RUN_DIR}/backend.log"
FRONTEND_LOG_FILE="${RUN_DIR}/frontend.log"

# --- Functions ---

# load_env_defaults
# Purpose: Loads environment variables from a .env file if they are not already set.
# Inputs:
#   $1 - Path to the environment file.
# Outputs:
#   Exports variables to the current shell.
# Exit Behavior: Returns 0.
load_env_defaults() {
  local env_file="$1"
  local line key value

  [ -f "${env_file}" ] || return 0

  while IFS= read -r line || [ -n "${line}" ]; do
    line="${line%$'\r'}"
    if [[ -z "${line}" || "${line}" == \#* ]]; then
      continue
    fi
    key="${line%%=*}"
    value="${line#*=}"
    if [[ -z "${key}" || "${key}" == "${line}" ]]; then
      continue
    fi
    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi
    if [ -n "${!key+x}" ]; then
      continue
    fi
    if [[ "${value}" =~ ^\".*\"$ || "${value}" =~ ^\'.*\'$ ]]; then
      value="${value:1:${#value}-2}"
    fi
    export "${key}=${value}"
  done <"${env_file}"
}

# ensure_run_dir
# Purpose: Ensures the runtime directory exists.
# Inputs: None.
# Outputs: None.
# Exit Behavior: Returns 0 if directory is created or exists.
ensure_run_dir() {
  mkdir -p "${RUN_DIR}"
}

# is_process_alive
# Purpose: Checks if a process with a given PID is currently running.
# Inputs:
#   $1 - PID to check.
# Outputs: None.
# Exit Behavior: Returns 0 if alive, non-zero otherwise.
is_process_alive() {
  local pid="$1"
  local kill_output

  [ -n "${pid}" ] || return 1

  if kill_output="$(kill -0 "${pid}" 2>&1)"; then
    return 0
  fi

  # In restricted shells, kill -0 may report EPERM even though the process
  # exists. Treat visible PIDs as alive so status checks do not delete valid
  # PID files owned by a less-restricted launcher.
  [ -d "/proc/${pid}" ] && return 0
  printf '%s' "${kill_output}" | grep -qi 'not permitted'
}

# read_pid_file
# Purpose: Reads a PID from a file, removing whitespace.
# Inputs:
#   $1 - Path to the PID file.
# Outputs:
#   Prints the PID to stdout.
# Exit Behavior: Returns 0.
read_pid_file() {
  local pid_file="$1"
  if [ -f "${pid_file}" ]; then
    tr -d '[:space:]' <"${pid_file}"
  fi
}

# clear_stale_pid_file
# Purpose: Removes a PID file if the process it refers to is no longer running.
# Inputs:
#   $1 - Path to the PID file.
# Outputs: None.
# Exit Behavior: Returns 0.
clear_stale_pid_file() {
  local pid_file="$1"
  local pid
  pid="$(read_pid_file "${pid_file}")"
  if [ -n "${pid}" ] && ! is_process_alive "${pid}"; then
    rm -f "${pid_file}"
  fi
}

# wait_for_url
# Purpose: Waits for a URL to return a successful response (HTTP 2xx).
# Inputs:
#   $1 - URL to check.
#   $2 - Timeout in seconds.
# Outputs: None.
# Exit Behavior: Returns 0 on success, 1 on timeout.
wait_for_url() {
  local url="$1"
  local timeout_seconds="$2"
  local waited=0

  while [ "${waited}" -lt "${timeout_seconds}" ]; do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

# find_port_process
# Purpose: Finds the PID of the process listening on a given TCP port.
# Inputs:
#   $1 - Port number.
# Outputs:
#   Prints the PID to stdout if found.
# Exit Behavior: Returns 0.
find_port_process() {
  local port="$1"
  lsof -ti "tcp:${port}" 2>/dev/null | head -n 1 || true
}

# terminate_pid
# Purpose: Attempts to stop a process gracefully, then forcefully if it persists.
# Inputs:
#   $1 - PID to terminate.
#   $2 - Descriptive name of the process.
#   $3 - Optional signal (defaults to TERM).
# Outputs:
#   Prints a warning to stderr if the process fails to stop.
# Exit Behavior: Returns 0 on success, 1 if process remains alive.
terminate_pid() {
  local pid="$1"
  local name="$2"
  local signal="${3:-TERM}"

  if ! is_process_alive "${pid}"; then
    return 0
  fi

  kill "-${signal}" "${pid}" >/dev/null 2>&1 || true

  local waited=0
  while is_process_alive "${pid}" && [ "${waited}" -lt 10 ]; do
    sleep 1
    waited=$((waited + 1))
  done

  if is_process_alive "${pid}"; then
    kill -KILL "${pid}" >/dev/null 2>&1 || true
  fi

  if is_process_alive "${pid}"; then
    printf '%s\n' "Warning: ${name} process ${pid} did not stop cleanly." >&2
    return 1
  fi
  return 0
}

# print_runtime_header
# Purpose: Prints key path information for the current environment.
# Inputs: None.
# Outputs:
#   Prints key-value pairs to stdout.
# Exit Behavior: Returns 0.
print_runtime_header() {
  printf '%s\n' \
    "repo_root=${REPO_ROOT}" \
    "env_file=$([ -f "${ENV_FILE}" ] && printf '%s' "${ENV_FILE}" || printf '%s' 'none')" \
    "run_dir=${RUN_DIR}"
}
