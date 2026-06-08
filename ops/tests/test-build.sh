#!/usr/bin/env bash
#
# test-build.sh
#
# Purpose:
#   Unit tests for the root build.sh command. Verifies dry-run output and
#   option handling without running Maven or npm.
#
# Usage:
#   ./ops/tests/test-build.sh
#
# Required Tools:
#   - bash
#
# Expected Output:
#   Success message: "build tests passed"
#   Failure message and non-zero exit code if assertions fail.
#
# Exit Behavior:
#   Exits with 0 on success, 1 on any test failure.
#

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="${REPO_ROOT}/build.sh"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

assert_not_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" == *"${needle}"* ]]; then
    printf 'expected output not to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

test_help_prints_usage() {
  local output
  output="$(bash "${SCRIPT_PATH}" --help)"

  assert_contains "${output}" 'build.sh'
  assert_contains "${output}" './build.sh [--skip-tests] [--clean-frontend]'
  assert_contains "${output}" '--skip-tests'
}

test_default_dry_run_builds_all_artifacts_with_tests() {
  local output
  output="$(BUILD_DRY_RUN=true bash "${SCRIPT_PATH}")"

  assert_contains "${output}" 'dry_run=true'
  assert_contains "${output}" 'mvn clean package'
  assert_not_contains "${output}" '-DskipTests'
  assert_contains "${output}" 'npm run build'
  assert_contains "${output}" 'Build MCP server'
  assert_contains "${output}" 'Build completed successfully.'
}

test_skip_tests_maps_to_maven_skip_flag() {
  local output
  output="$(BUILD_DRY_RUN=true bash "${SCRIPT_PATH}" --skip-tests)"

  assert_contains "${output}" 'skip_tests=true'
  assert_contains "${output}" 'mvn clean package -DskipTests'
}

test_clean_frontend_removes_dist_only_when_requested() {
  local output
  output="$(BUILD_DRY_RUN=true bash "${SCRIPT_PATH}" --clean-frontend)"

  assert_contains "${output}" 'clean_frontend=true'
  assert_contains "${output}" 'rm -rf'
  assert_contains "${output}" 'frontend/dist'
}

test_unknown_option_fails() {
  if BUILD_DRY_RUN=true bash "${SCRIPT_PATH}" --unknown >/tmp/build-unknown.out 2>/tmp/build-unknown.err; then
    printf 'expected unknown build option to fail\n' >&2
    exit 1
  fi

  assert_contains "$(cat /tmp/build-unknown.err)" 'Unknown option: --unknown'
  rm -f /tmp/build-unknown.out /tmp/build-unknown.err
}

main() {
  test_help_prints_usage
  test_default_dry_run_builds_all_artifacts_with_tests
  test_skip_tests_maps_to_maven_skip_flag
  test_clean_frontend_removes_dist_only_when_requested
  test_unknown_option_fails
  printf 'build tests passed\n'
}

main "$@"
