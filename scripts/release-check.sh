#!/usr/bin/env bash
#
# release-check.sh
#
# Purpose:
#   Runs the local pre-release validation gate from one command.
#
# Usage:
#   ./scripts/release-check.sh
#   RELEASE_CHECK_DOCKER=true ./scripts/release-check.sh
#
# Required Tools:
#   - bash
#   - make
#   - git
#   - Java 21 and Maven for backend checks
#   - Node.js and npm for frontend/MCP checks
#   - Docker and Trivy only when RELEASE_CHECK_DOCKER=true
#
# Exit Behavior:
#   Exits with 0 on success.
#   Exits with a non-zero status as soon as a required validation step fails.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RELEASE_CHECK_DOCKER="${RELEASE_CHECK_DOCKER:-false}"

require_command() {
  local command_name="$1"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    printf 'Error: required command not found: %s\n' "${command_name}" >&2
    exit 1
  fi
}

normalize_bool() {
  case "${1}" in
    true|TRUE|True|1|yes|YES|Yes|y|Y)
      printf '%s\n' 'true'
      ;;
    false|FALSE|False|0|no|NO|No|n|N|'')
      printf '%s\n' 'false'
      ;;
    *)
      printf 'Error: expected boolean true/false, got: %s\n' "${1}" >&2
      exit 1
      ;;
  esac
}

run_step() {
  local title="$1"
  shift

  printf '\n'
  printf '==================== %s ====================\n' "${title}"
  "$@"
}

require_command make
require_command git

release_check_docker_normalized="$(normalize_bool "${RELEASE_CHECK_DOCKER}")"

if [ "${release_check_docker_normalized}" = 'true' ]; then
  require_command docker
  require_command trivy
fi

cd "${REPO_ROOT}"

printf '%s\n' \
  'Local GenAI Lab release check' \
  'scope: tests, builds, dependency freshness, whitespace checks' \
  "docker full check: ${release_check_docker_normalized}"

run_step 'normal test suite' make test
run_step 'broader verification suite' make verify
run_step 'dependency freshness report' make dependency-freshness
run_step 'whitespace check' git diff --check

if [ "${release_check_docker_normalized}" = 'true' ]; then
  run_step 'Docker full check' make docker-full-check
else
  printf '\n'
  printf '%s\n' \
    '==================== Docker full check ====================' \
    'skipped: set RELEASE_CHECK_DOCKER=true to run make docker-full-check'
fi

printf '\n'
printf '%s\n' 'release check passed'
