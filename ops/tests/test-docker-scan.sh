#!/usr/bin/env bash
#
# test-docker-scan.sh
#
# Purpose:
#   Unit tests for docker-scan.sh using a mocked Trivy executable.
#
# Usage:
#   ./ops/tests/test-docker-scan.sh
#
# Required Tools:
#   - bash
#   - mktemp
#
# Expected Output:
#   Success message: "docker scan tests passed"
#   Failure message and non-zero exit code if assertions fail.
#
# Exit Behavior:
#   Exits with 0 on success, 1 on any test failure.
#

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="${REPO_ROOT}/docker-scan.sh"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

assert_file_contains() {
  local path="$1"
  local needle="$2"
  local contents

  contents="$(cat "${path}")"
  assert_contains "${contents}" "${needle}"
}

write_mock_trivy() {
  local bin_dir="$1"

  cat >"${bin_dir}/trivy" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${MOCK_TRIVY_LOG}"
if [ "${MOCK_TRIVY_FAIL:-false}" = "true" ]; then
  printf '%s\n' 'mock vulnerability findings' >&2
  exit 1
fi
printf '%s\n' 'mock trivy scan ok'
EOF

  chmod +x "${bin_dir}/trivy"
}

run_scan() {
  local tmp_dir="$1"
  shift

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:/usr/bin:/bin" \
    MOCK_TRIVY_LOG="${tmp_dir}/trivy.log" \
    "$@" \
    bash "${SCRIPT_PATH}"
}

test_missing_trivy_is_actionable() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"

  set +e
  output="$(run_scan "${tmp_dir}" 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-scan.sh to fail when trivy is missing' >&2
    exit 1
  fi
  assert_contains "${output}" 'Error: trivy was not found.'
  assert_contains "${output}" 'macOS: brew install trivy'
  rm -rf "${tmp_dir}"
}

test_advisory_mode_scans_expected_images() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/trivy.log"
  write_mock_trivy "${tmp_dir}/bin"

  output="$(run_scan "${tmp_dir}")"

  assert_contains "${output}" 'Docker security scan'
  assert_contains "${output}" 'severity: HIGH,CRITICAL'
  assert_contains "${output}" 'mode: advisory'
  assert_contains "${output}" 'Scanning Docker image: local-genai-lab-backend'
  assert_contains "${output}" 'Scanning Docker image: local-genai-lab-frontend'
  assert_contains "${output}" 'Scanning Docker image: qdrant/qdrant:v1.18.2'
  assert_contains "${output}" 'Docker security scan completed.'
  assert_file_contains "${tmp_dir}/trivy.log" 'image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 0 local-genai-lab-backend'
  assert_file_contains "${tmp_dir}/trivy.log" 'image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 0 local-genai-lab-frontend'
  assert_file_contains "${tmp_dir}/trivy.log" 'image --severity HIGH,CRITICAL --ignore-unfixed --exit-code 0 qdrant/qdrant:v1.18.2'
  rm -rf "${tmp_dir}"
}

test_strict_mode_fails_on_trivy_findings() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/trivy.log"
  write_mock_trivy "${tmp_dir}/bin"

  set +e
  output="$(run_scan "${tmp_dir}" DOCKER_SCAN_STRICT=true MOCK_TRIVY_FAIL=true 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected strict docker scan to fail when trivy reports findings' >&2
    exit 1
  fi
  assert_contains "${output}" 'mode: strict'
  assert_contains "${output}" 'Docker security scan found configured-severity issues in strict mode, or Trivy could not complete a scan.'
  assert_file_contains "${tmp_dir}/trivy.log" '--exit-code 1 local-genai-lab-backend'
  rm -rf "${tmp_dir}"
}

test_advisory_mode_reports_trivy_runtime_failure() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/trivy.log"
  write_mock_trivy "${tmp_dir}/bin"

  set +e
  output="$(run_scan "${tmp_dir}" MOCK_TRIVY_FAIL=true 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected advisory docker scan to fail when Trivy cannot complete scans' >&2
    exit 1
  fi
  assert_contains "${output}" 'mode: advisory'
  assert_contains "${output}" 'Docker security scan could not complete. Check the Trivy error output above.'
  rm -rf "${tmp_dir}"
}

test_qdrant_scan_can_be_skipped() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/trivy.log"
  write_mock_trivy "${tmp_dir}/bin"

  output="$(run_scan "${tmp_dir}" DOCKER_SCAN_INCLUDE_QDRANT=false)"

  assert_contains "${output}" 'Skipping Qdrant image scan because DOCKER_SCAN_INCLUDE_QDRANT=false.'
  assert_file_contains "${tmp_dir}/trivy.log" 'local-genai-lab-backend'
  if grep -Fq 'qdrant/qdrant' "${tmp_dir}/trivy.log"; then
    printf '%s\n' 'expected qdrant image not to be scanned' >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

main() {
  test_missing_trivy_is_actionable
  test_advisory_mode_scans_expected_images
  test_strict_mode_fails_on_trivy_findings
  test_advisory_mode_reports_trivy_runtime_failure
  test_qdrant_scan_can_be_skipped
  printf 'docker scan tests passed\n'
}

main "$@"
