#!/usr/bin/env bash

set -u

RUNTIME_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${RUNTIME_SCRIPT_DIR}/../.." && pwd)"
RUN_DIR="${REPO_ROOT}/.run"
ENV_FILE="${ENV_FILE:-${REPO_ROOT}/.env}"

BACKEND_PID_FILE="${RUN_DIR}/backend.pid"
FRONTEND_PID_FILE="${RUN_DIR}/frontend.pid"
BACKEND_LOG_FILE="${RUN_DIR}/backend.log"
FRONTEND_LOG_FILE="${RUN_DIR}/frontend.log"

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
  done < "${env_file}"
}

ensure_run_dir() {
  mkdir -p "${RUN_DIR}"
}

is_process_alive() {
  local pid="$1"
  [ -n "${pid}" ] && kill -0 "${pid}" >/dev/null 2>&1
}

read_pid_file() {
  local pid_file="$1"
  if [ -f "${pid_file}" ]; then
    tr -d '[:space:]' < "${pid_file}"
  fi
}

clear_stale_pid_file() {
  local pid_file="$1"
  local pid
  pid="$(read_pid_file "${pid_file}")"
  if [ -n "${pid}" ] && ! is_process_alive "${pid}"; then
    rm -f "${pid_file}"
  fi
}

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

find_port_process() {
  local port="$1"
  lsof -ti "tcp:${port}" 2>/dev/null | head -n 1 || true
}

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

print_runtime_header() {
  printf '%s\n' \
    "repo_root=${REPO_ROOT}" \
    "env_file=$([ -f "${ENV_FILE}" ] && printf '%s' "${ENV_FILE}" || printf '%s' 'none')" \
    "run_dir=${RUN_DIR}"
}
