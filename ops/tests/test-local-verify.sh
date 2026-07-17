#!/usr/bin/env bash
#
# test-local-verify.sh
#
# Purpose:
#   Unit tests for scripts/local-verify.sh using mocked toolchain commands.
#
# Usage:
#   ./ops/tests/test-local-verify.sh
#

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="${REPO_ROOT}/scripts/local-verify.sh"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

write_mock_toolchain() {
  local bin_dir="$1"

  cat >"${bin_dir}/java" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${MOCK_JAVA_VERSION:-openjdk 21.0.11 2026-04-21 LTS}"
EOF

  cat >"${bin_dir}/mvn" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [ "${1:-}" = "--version" ]; then
  printf '%s\n' 'Apache Maven 3.9.15'
  exit 0
fi
printf 'mvn %s\n' "$*" >> "${MOCK_LOCAL_VERIFY_LOG}"
EOF

  cat >"${bin_dir}/node" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${MOCK_NODE_VERSION:-v24.18.0}"
EOF

  cat >"${bin_dir}/npm" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' '12.0.1'
EOF

  cat >"${bin_dir}/make" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'make %s\n' "$*" >> "${MOCK_LOCAL_VERIFY_LOG}"
printf 'mock make %s\n' "$*"
if [ "${MOCK_FAIL_TARGET:-}" = "$*" ]; then
  printf 'mock failure for %s\n' "$*" >&2
  exit 1
fi
EOF

  chmod +x "${bin_dir}/java" "${bin_dir}/mvn" "${bin_dir}/node" "${bin_dir}/npm" "${bin_dir}/make"
}

run_local_verify() {
  local tmp_dir="$1"
  shift

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:/usr/bin:/bin" \
    MOCK_LOCAL_VERIFY_LOG="${tmp_dir}/local-verify.log" \
    MOCK_JAVA_VERSION="${MOCK_JAVA_VERSION:-}" \
    MOCK_NODE_VERSION="${MOCK_NODE_VERSION:-}" \
    MOCK_FAIL_TARGET="${MOCK_FAIL_TARGET:-}" \
    bash "${SCRIPT_PATH}" \
    "$@"
}

test_help_prints_usage() {
  local output
  output="$(bash "${SCRIPT_PATH}" --help)"

  assert_contains "${output}" './scripts/local-verify.sh --quick'
  assert_contains "${output}" './scripts/local-verify.sh --full'
  assert_contains "${output}" 'Each command writes full output to /tmp/local-genai-lab-<step>.txt.'
}

test_quick_mode_runs_expected_make_targets() {
  local tmp_dir output log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/local-verify.log"
  write_mock_toolchain "${tmp_dir}/bin"

  output="$(run_local_verify "${tmp_dir}" --quick)"
  log="$(cat "${tmp_dir}/local-verify.log")"

  assert_contains "${output}" 'Local verification toolchain'
  assert_contains "${output}" 'pass: backend tests'
  assert_contains "${output}" 'pass: frontend tests'
  assert_contains "${output}" 'pass: frontend build'
  assert_contains "${log}" 'make test-backend'
  assert_contains "${log}" 'make test-frontend'
  assert_contains "${log}" 'make build-frontend'
  rm -rf "${tmp_dir}"
}

test_full_mode_runs_make_verify() {
  local tmp_dir output log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/local-verify.log"
  write_mock_toolchain "${tmp_dir}/bin"

  output="$(run_local_verify "${tmp_dir}")"
  log="$(cat "${tmp_dir}/local-verify.log")"

  assert_contains "${output}" 'pass: full local verification'
  assert_contains "${log}" 'make verify'
  rm -rf "${tmp_dir}"
}

test_step_failure_reports_log_path() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/local-verify.log"
  write_mock_toolchain "${tmp_dir}/bin"

  set +e
  output="$(MOCK_FAIL_TARGET='test-frontend' run_local_verify "${tmp_dir}" --quick 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected local-verify quick mode to fail when a step fails' >&2
    exit 1
  fi
  assert_contains "${output}" 'fail: frontend tests'
  assert_contains "${output}" '/tmp/local-genai-lab-test-frontend.txt'
  rm -rf "${tmp_dir}"
}

test_old_java_version_fails_fast() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/local-verify.log"
  write_mock_toolchain "${tmp_dir}/bin"

  set +e
  output="$(MOCK_JAVA_VERSION='openjdk 17.0.12 2025-07-16 LTS' run_local_verify "${tmp_dir}" 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected local-verify to fail when Java is below the supported version' >&2
    exit 1
  fi
  assert_contains "${output}" 'Error: Java 21 or newer is required'
  rm -rf "${tmp_dir}"
}

main() {
  test_help_prints_usage
  test_quick_mode_runs_expected_make_targets
  test_full_mode_runs_make_verify
  test_step_failure_reports_log_path
  test_old_java_version_fails_fast
  printf 'local verify tests passed\n'
}

main "$@"
