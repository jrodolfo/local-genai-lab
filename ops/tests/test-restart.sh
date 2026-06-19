#!/usr/bin/env bash
#
# test-restart.sh
#
# Purpose:
#   Unit tests for restart.sh output behavior.
#
# Usage:
#   ./ops/tests/test-restart.sh
#
# Required Tools:
#   - bash
#   - mktemp
#
# Expected Output:
#   Success message: "restart tests passed"
#
# Exit Behavior:
#   Exits with 0 on success, 1 on any test failure.
#

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="${REPO_ROOT}/restart.sh"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

write_mock_scripts() {
  local bin_dir="$1"
  mkdir -p "${bin_dir}/ops/lib"
  cp "${REPO_ROOT}/ops/lib/runtime-common.sh" "${bin_dir}/ops/lib/runtime-common.sh"
  cp "${SCRIPT_PATH}" "${bin_dir}/restart.sh"
  chmod +x "${bin_dir}/restart.sh"

  cat >"${bin_dir}/stop.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [ "${MOCK_STOP_FAIL:-false}" = "true" ]; then
  printf '%s\n' 'mock stop failed'
  exit 7
fi
printf '%s\n' 'mock stop ok'
EOF

  cat >"${bin_dir}/start.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' 'mock start ok'
EOF

  chmod +x "${bin_dir}/stop.sh" "${bin_dir}/start.sh"
}

write_mock_lsof() {
  local bin_dir="$1"

  cat >"${bin_dir}/lsof" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "${*: -1}" in
  tcp:5173)
    printf '%s\n' "${MOCK_FRONTEND_PORT_PID:-}"
    ;;
  tcp:8080)
    printf '%s\n' "${MOCK_BACKEND_PORT_PID:-}"
    ;;
esac
EOF

  chmod +x "${bin_dir}/lsof"
}

run_restart() {
  local tmp_dir="$1"
  shift
  local env_file="${tmp_dir}/test.env"
  : >"${env_file}"

  env \
    PATH="${tmp_dir}/bin:${PATH}" \
    ENV_FILE="${env_file}" \
    RUN_DIR="${tmp_dir}/run" \
    "$@" \
    bash "${tmp_dir}/bin/restart.sh"
}

test_restart_success_prints_summary() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_scripts "${tmp_dir}/bin"
  write_mock_lsof "${tmp_dir}/bin"

  output="$(run_restart "${tmp_dir}")"

  assert_contains "${output}" 'restart completed'
  assert_contains "${output}" 'urls:'
  assert_contains "${output}" '  backend: http://localhost:8080'
  assert_contains "${output}" '  frontend: http://localhost:5173'
  assert_contains "${output}" 'logs:'
  assert_contains "${output}" "  backend: ${tmp_dir}/run/backend.log"
  assert_contains "${output}" "  frontend: ${tmp_dir}/run/frontend.log"
  rm -rf "${tmp_dir}"
}

test_restart_stop_failure_prints_actionable_summary() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_scripts "${tmp_dir}/bin"
  write_mock_lsof "${tmp_dir}/bin"

  set +e
  output="$(MOCK_STOP_FAIL=true MOCK_FRONTEND_PORT_PID=12345 run_restart "${tmp_dir}")"
  status=$?
  set -e

  [ "${status}" -eq 7 ] || {
    printf 'expected exit status 7, got %s\noutput:\n%s\n' "${status}" "${output}" >&2
    exit 1
  }
  assert_contains "${output}" 'restart failed'
  assert_contains "${output}" 'reason: frontend port 5173 is still owned by pid 12345'
  assert_contains "${output}" 'next step:'
  assert_contains "${output}" 'urls:'
  assert_contains "${output}" 'logs:'
  rm -rf "${tmp_dir}"
}

main() {
  test_restart_success_prints_summary
  test_restart_stop_failure_prints_actionable_summary
  printf 'restart tests passed\n'
}

main "$@"
