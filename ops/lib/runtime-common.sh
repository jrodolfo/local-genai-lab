#!/usr/bin/env bash
#
# runtime-common.sh
#
# Purpose:
#   Provides shared utility functions and environment configuration for local
#   runtime management (backend/frontend).
#
# Design Notes:
#   Functions are intentionally POSIX-ish Bash helpers used by root scripts.
#   load_env_defaults never overwrites variables already set in the caller's
#   shell, so one-off commands such as SERVER_PORT=8090 ./start.sh work.
#
# Usage:
#   source ops/lib/runtime-common.sh
#
# Required Tools:
#   - curl (for wait_for_url)
#   - lsof or netstat (for find_port_process)
#   - docker (only for Qdrant vector-store startup)
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
#          Existing shell variables win over .env values.
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
  local kill_output tasklist_output

  [ -n "${pid}" ] || return 1

  if kill_output="$(kill -0 "${pid}" 2>&1)"; then
    return 0
  fi

  if printf '%s' "${kill_output}" | grep -qi 'no such process'; then
    return 1
  fi

  if [ "${OS:-}" = "Windows_NT" ] && command -v tasklist >/dev/null 2>&1; then
    if tasklist_output="$(tasklist //FI "PID eq ${pid}" //NH 2>&1)"; then
      printf '%s\n' "${tasklist_output}" \
        | awk -v pid="${pid}" '$2 == pid { found = 1 } END { exit !found }'
      return $?
    fi
    if printf '%s' "${tasklist_output}" | grep -qi 'access denied'; then
      return 0
    fi
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

curl_probe_url() {
  local url="$1"
  curl -fsS --connect-timeout 1 --max-time 2 "${url}" >/dev/null 2>&1
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
    if curl_probe_url "${url}"; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

# wait_for_url_with_dots
# Purpose: Waits for a URL like wait_for_url, printing a dot every few seconds
#          while the wait is still active.
# Inputs:
#   $1 - URL to check.
#   $2 - Timeout in seconds.
#   $3 - Optional dot interval in seconds. Defaults to 1.
# Outputs:
#   Prints progress dots to stdout.
# Exit Behavior: Returns 0 on success, 1 on timeout.
wait_for_url_with_dots() {
  local url="$1"
  local timeout_seconds="$2"
  local dot_interval="${3:-1}"
  local waited=0
  local printed_dots='false'

  while [ "${waited}" -lt "${timeout_seconds}" ]; do
    if curl_probe_url "${url}"; then
      if [ "${printed_dots}" = 'true' ]; then
        printf '\n'
      fi
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
    if [ "${dot_interval}" -gt 0 ] && [ $((waited % dot_interval)) -eq 0 ]; then
      printf '.'
      printed_dots='true'
    fi
  done

  if [ "${printed_dots}" = 'true' ]; then
    printf '\n'
  fi
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
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti "tcp:${port}" 2>/dev/null | head -n 1 || true
    return 0
  fi

  if command -v netstat >/dev/null 2>&1; then
    netstat -ano -p tcp 2>/dev/null \
      | awk -v port=":${port}" '$1 == "TCP" && $2 ~ port "$" && $4 == "LISTENING" { print $5; exit }' \
      | tr -d '\r'
  fi
}

kill_command_hint() {
  local pid="$1"
  case "$(uname -s 2>/dev/null || printf unknown)" in
    MINGW* | MSYS* | CYGWIN*)
      printf 'taskkill //PID %s //T //F' "${pid}"
      ;;
    *)
      printf 'kill -TERM %s' "${pid}"
      ;;
  esac
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

  if [ "${OS:-}" = "Windows_NT" ] && command -v taskkill >/dev/null 2>&1; then
    taskkill //PID "${pid}" //T //F >/dev/null 2>&1 || true
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

print_runtime_endpoints_and_logs() {
  local backend_url="${1:-http://localhost:${SERVER_PORT:-8080}}"
  local frontend_url="${2:-http://localhost:${FRONTEND_PORT:-5173}}"

  printf '%s\n' \
    'urls:' \
    "  backend: ${backend_url}" \
    "  frontend: ${frontend_url}" \
    'logs:' \
    "  backend: ${BACKEND_LOG_FILE}" \
    "  frontend: ${FRONTEND_LOG_FILE}"
}

# normalize_lower
# Purpose: Normalizes a value to lowercase.
# Inputs:
#   $1 - Value to normalize.
# Outputs:
#   Prints the normalized value to stdout.
# Exit Behavior: Returns 0.
normalize_lower() {
  printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]'
}

# normalize_bool
# Purpose: Normalizes common truthy values to true; everything else is false.
# Inputs:
#   $1 - Value to normalize.
# Outputs:
#   Prints true or false to stdout.
# Exit Behavior: Returns 0.
normalize_bool() {
  local value
  value="$(normalize_lower "${1:-}")"
  case "${value}" in
    true | 1 | yes | y | on)
      printf '%s' 'true'
      ;;
    *)
      printf '%s' 'false'
      ;;
  esac
}

# qdrant_required_for_current_config
# Purpose: Determines whether the current RAG configuration requires Qdrant.
#          This is strict: startup should fail if Qdrant cannot be started.
# Inputs: Environment variables RAG_ENABLED, RAG_RETRIEVAL_MODE, RAG_VECTOR_STORE.
# Outputs: None.
# Exit Behavior: Returns 0 if Qdrant is required, 1 otherwise.
qdrant_required_for_current_config() {
  local rag_enabled rag_retrieval_mode rag_vector_store

  rag_enabled="$(normalize_bool "${RAG_ENABLED:-true}")"
  rag_retrieval_mode="$(normalize_lower "${RAG_RETRIEVAL_MODE:-lexical}")"
  rag_vector_store="$(normalize_lower "${RAG_VECTOR_STORE:-in-memory}")"

  [ "${rag_enabled}" = 'true' ] \
    && [ "${rag_retrieval_mode}" = 'vector' ] \
    && [ "${rag_vector_store}" = 'qdrant' ]
}

# qdrant_auto_start_enabled
# Purpose: Determines whether startup should also try to start Qdrant for the
#          optional RAG comparison target. This is best-effort unless the active
#          backend configuration explicitly requires Qdrant.
# Inputs: Environment variables RAG_ENABLED, RAG_QDRANT_AUTO_START.
# Outputs: None.
# Exit Behavior: Returns 0 if opportunistic Qdrant startup is enabled.
qdrant_auto_start_enabled() {
  local rag_enabled qdrant_auto_start

  rag_enabled="$(normalize_bool "${RAG_ENABLED:-true}")"
  qdrant_auto_start="$(normalize_bool "${RAG_QDRANT_AUTO_START:-true}")"

  [ "${rag_enabled}" = 'true' ] && [ "${qdrant_auto_start}" = 'true' ]
}

# start_qdrant_service
# Purpose: Starts Qdrant through Docker Compose.
# Inputs:
#   $1 - "strict" to fail on Docker/Qdrant errors, otherwise best-effort.
# Outputs: Prints startup status and any warnings/errors.
# Exit Behavior: Returns non-zero only in strict mode when startup fails.
start_qdrant_service() {
  local mode="$1"
  local strict='false'

  if [ "${mode}" = 'strict' ]; then
    strict='true'
  fi

  if ! command -v docker >/dev/null 2>&1; then
    if [ "${strict}" = 'true' ]; then
      printf '%s\n' \
        'Error: RAG_VECTOR_STORE=qdrant requires Docker, but docker was not found.' \
        'Install/start Docker or use RAG_VECTOR_STORE=in-memory.' >&2
      return 1
    fi
    printf '%s\n' \
      'Warning: Qdrant auto-start skipped because docker was not found.' \
      'Vector - Qdrant comparison will be unavailable until Qdrant is running.' >&2
    return 0
  fi

  printf '%s\n' 'qdrant: starting via docker compose'
  if ! (cd "${REPO_ROOT}" && docker compose up -d qdrant); then
    if [ "${strict}" = 'true' ]; then
      printf '%s\n' \
        'Error: failed to start Qdrant with docker compose.' \
        'Check Docker is running, or use RAG_VECTOR_STORE=in-memory.' >&2
      return 1
    fi
    printf '%s\n' \
      'Warning: failed to auto-start Qdrant with docker compose.' \
      'The app will continue, but Vector - Qdrant comparison will be unavailable until Qdrant is running.' >&2
    return 0
  fi
}

# ensure_qdrant_service_for_runtime
# Purpose: Starts Qdrant when required by the active backend config, or tries to
#          start it opportunistically for the optional RAG comparison target.
# Inputs: Environment variables RAG_ENABLED, RAG_RETRIEVAL_MODE, RAG_VECTOR_STORE,
#         RAG_QDRANT_AUTO_START.
# Outputs: Prints startup status when Qdrant startup is attempted.
# Exit Behavior: Returns non-zero only when the active config requires Qdrant.
ensure_qdrant_service_for_runtime() {
  if qdrant_required_for_current_config; then
    start_qdrant_service strict
    return $?
  fi

  if qdrant_auto_start_enabled; then
    start_qdrant_service best_effort
  fi
}

# ensure_qdrant_service_if_required
# Purpose: Backward-compatible helper for callers that only want strict
#          Qdrant startup when the active backend config requires it.
ensure_qdrant_service_if_required() {
  if qdrant_required_for_current_config; then
    start_qdrant_service strict
  fi
}
