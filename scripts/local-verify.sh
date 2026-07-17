#!/usr/bin/env bash
#
# local-verify.sh
#
# Purpose:
#   Runs the supported local verification flow for this repository with an
#   explicit toolchain preflight and `/tmp` log files that are easy to inspect
#   on EC2 or other remote Linux hosts.
#
# Usage:
#   ./scripts/local-verify.sh
#   ./scripts/local-verify.sh --quick
#   ./scripts/local-verify.sh --full
#   ./scripts/local-verify.sh --help
#
# Required Tools:
#   - bash
#   - java
#   - mvn
#   - node
#   - npm
#   - make
#
# Expected Output:
#   Toolchain versions, pass/fail lines for each verification step, and the
#   `/tmp` log locations for deeper inspection.
#
# Exit Behavior:
#   Exits with 0 when the selected verification flow passes.
#   Exits non-zero when required tools are missing, versions are unsupported,
#   or any verification step fails.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

print_usage() {
  printf '%s\n' \
    'Usage:' \
    '  ./scripts/local-verify.sh' \
    '  ./scripts/local-verify.sh --quick' \
    '  ./scripts/local-verify.sh --full' \
    '  ./scripts/local-verify.sh --help' \
    '' \
    'Modes:' \
    '  --quick   run backend tests, frontend tests, and frontend build.' \
    '  --full    run make verify.' \
    '            This is also the default when no mode is provided.' \
    '' \
    'Logs:' \
    '  Each command writes full output to /tmp/local-genai-lab-<step>.txt.' \
    '' \
    'EC2 note:' \
    '  This script is intended to make the supported local verification path' \
    '  explicit on remote Linux or EC2 hosts where long command output is' \
    '  easier to inspect in /tmp files than in the terminal scrollback.'
}

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    printf 'Error: required command not found: %s\n' "${command_name}" >&2
    exit 1
  fi
}

major_version() {
  local version_text="$1"
  printf '%s' "${version_text}" | sed -E 's/^[^0-9]*([0-9]+).*/\1/'
}

print_toolchain_summary() {
  local java_version maven_version node_version npm_version make_version

  java_version="$(java --version | head -n 1)"
  maven_version="$(mvn --version | head -n 1)"
  node_version="$(node --version)"
  npm_version="$(npm --version)"
  make_version="$(make --version)"

  printf '%s\n' 'Local verification toolchain'
  printf '  java:   %s\n' "${java_version}"
  printf '  maven:  %s\n' "${maven_version}"
  printf '  node:   %s\n' "${node_version}"
  printf '  npm:    %s\n' "${npm_version}"
  printf '  make:   %s\n' "$(printf '%s\n' "${make_version}" | sed -n '1p')"
  printf '\n'
}

validate_versions() {
  local java_major node_major

  java_major="$(major_version "$(java --version | head -n 1)")"
  node_major="$(major_version "$(node --version)")"

  if [ "${java_major}" -lt 21 ]; then
    printf 'Error: Java 21 or newer is required, found: %s\n' "$(java --version | head -n 1)" >&2
    exit 1
  fi

  if [ "${node_major}" -lt 20 ]; then
    printf 'Error: Node.js 20 or newer is required, found: %s\n' "$(node --version)" >&2
    exit 1
  fi
}

run_step() {
  local step_name="$1"
  local log_path="$2"
  shift 2

  printf 'running: %s\n' "${step_name}"
  if (
    cd "${REPO_ROOT}"
    "$@"
  ) >"${log_path}" 2>&1; then
    printf 'pass: %s\n' "${step_name}"
    printf '  log: %s\n' "${log_path}"
    return 0
  fi

  printf 'fail: %s\n' "${step_name}" >&2
  printf '  log: %s\n' "${log_path}" >&2
  printf '  tail:\n' >&2
  tail -n 80 "${log_path}" >&2 || true
  exit 1
}

mode='full'

while [ "$#" -gt 0 ]; do
  case "$1" in
    --quick)
      mode='quick'
      ;;
    --full)
      mode='full'
      ;;
    --help|-h)
      print_usage
      exit 0
      ;;
    *)
      printf 'Error: unsupported option: %s\n\n' "$1" >&2
      print_usage >&2
      exit 1
      ;;
  esac
  shift
done

require_command java
require_command mvn
require_command node
require_command npm
require_command make

validate_versions
print_toolchain_summary

if [ "${mode}" = 'quick' ]; then
  run_step 'backend tests' '/tmp/local-genai-lab-test-backend.txt' make test-backend
  run_step 'frontend tests' '/tmp/local-genai-lab-test-frontend.txt' make test-frontend
  run_step 'frontend build' '/tmp/local-genai-lab-build-frontend.txt' make build-frontend
else
  run_step 'full local verification' '/tmp/local-genai-lab-make-verify.txt' make verify
fi

printf '%s\n' '' 'Local verification completed successfully.'
