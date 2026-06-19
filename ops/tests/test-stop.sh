#!/usr/bin/env bash
#
# test-stop.sh
#
# Purpose:
#   Unit tests for the root stop.sh script. Verifies managed-only behavior and
#   --all behavior for configured port owners with mocked lsof output.
#
# Usage:
#   ./ops/tests/test-stop.sh
#
# Required Tools:
#   - bash
#   - mktemp
#   - sleep
#
# Expected Output:
#   Success message: "stop tests passed"
#   Failure message and non-zero exit code if assertions fail.
#
# Exit Behavior:
#   Exits with 0 on success, 1 on any test failure.
#

set -euo pipefail

# --- Path Definitions ---
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="${REPO_ROOT}/stop.sh"

# --- Test Helpers ---

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

assert_process_alive() {
  local pid="$1"
  if ! kill -0 "${pid}" >/dev/null 2>&1; then
    printf 'expected process to be alive: %s\n' "${pid}" >&2
    exit 1
  fi
}

assert_process_stopped() {
  local pid="$1"
  local waited=0
  while kill -0 "${pid}" >/dev/null 2>&1 && [ "${waited}" -lt 5 ]; do
    sleep 1
    waited=$((waited + 1))
  done
  if kill -0 "${pid}" >/dev/null 2>&1; then
    printf 'expected process to be stopped: %s\n' "${pid}" >&2
    exit 1
  fi
}

write_mock_lsof() {
  local bin_dir="$1"

  cat >"${bin_dir}/lsof" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "${*: -1}" in
  tcp:5173)
    if [ -n "${MOCK_STUCK_FRONTEND_PORT_PID:-}" ]; then
      printf '%s\n' "${MOCK_STUCK_FRONTEND_PORT_PID}"
      exit 0
    fi
    if [ -n "${MOCK_FRONTEND_PORT_PID:-}" ] && kill -0 "${MOCK_FRONTEND_PORT_PID}" >/dev/null 2>&1; then
      printf '%s\n' "${MOCK_FRONTEND_PORT_PID}"
    fi
    ;;
  tcp:8080)
    if [ -n "${MOCK_BACKEND_PORT_PID:-}" ] && kill -0 "${MOCK_BACKEND_PORT_PID}" >/dev/null 2>&1; then
      printf '%s\n' "${MOCK_BACKEND_PORT_PID}"
    fi
    ;;
esac
EOF

  chmod +x "${bin_dir}/lsof"
}

run_stop() {
  local tmp_dir="$1"
  shift
  local env_file="${tmp_dir}/test.env"
  : >"${env_file}"

  env \
    PATH="${tmp_dir}/bin:${PATH}" \
    ENV_FILE="${env_file}" \
    RUN_DIR="${tmp_dir}/run" \
    bash "${SCRIPT_PATH}" "$@"
}

# --- Test Cases ---

test_stop_without_all_leaves_unmanaged_port_owner_running() {
  local tmp_dir output port_pid
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_lsof "${tmp_dir}/bin"

  sleep 60 &
  port_pid="$!"

  output="$(MOCK_FRONTEND_PORT_PID="${port_pid}" run_stop "${tmp_dir}")"

  assert_contains "${output}" 'No managed local-genai-lab processes were running.'
  assert_process_alive "${port_pid}"
  kill "${port_pid}" >/dev/null 2>&1 || true
  wait "${port_pid}" 2>/dev/null || true
  rm -rf "${tmp_dir}"
}

test_stop_all_stops_unmanaged_frontend_port_owner() {
  local tmp_dir output port_pid
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_lsof "${tmp_dir}/bin"

  sleep 60 &
  port_pid="$!"

  output="$(MOCK_FRONTEND_PORT_PID="${port_pid}" run_stop "${tmp_dir}" --all)"

  assert_contains "${output}" "Stopping unmanaged frontend port owner (pid=${port_pid}, port=5173)..."
  assert_contains "${output}" "Stopped unmanaged frontend port owner (pid=${port_pid}, port=5173)."
  assert_process_stopped "${port_pid}"
  wait "${port_pid}" 2>/dev/null || true
  rm -rf "${tmp_dir}"
}

test_stop_all_reports_action_for_stuck_frontend_port_owner() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_lsof "${tmp_dir}/bin"

  set +e
  output="$(MOCK_STUCK_FRONTEND_PORT_PID=12345 run_stop "${tmp_dir}" --all 2>&1)"
  status=$?
  set -e

  [ "${status}" -ne 0 ] || {
    printf 'expected stop to fail for stuck frontend port owner\noutput:\n%s\n' "${output}" >&2
    exit 1
  }
  assert_contains "${output}" 'frontend did not stop: port 5173 is still owned by pid 12345.'
  assert_contains "${output}" 'next step:'
  rm -rf "${tmp_dir}"
}

# --- Main ---

main() {
  test_stop_without_all_leaves_unmanaged_port_owner_running
  test_stop_all_stops_unmanaged_frontend_port_owner
  test_stop_all_reports_action_for_stuck_frontend_port_owner
  printf 'stop tests passed\n'
}

main "$@"
