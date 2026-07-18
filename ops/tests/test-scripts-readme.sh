#!/usr/bin/env bash
#
# test-scripts-readme.sh
#
# Purpose:
#   Verifies that scripts/README.md retains the execution map for the main
#   direct-use orchestration scripts.
#
# Usage:
#   ./ops/tests/test-scripts-readme.sh
#

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
README_PATH="${REPO_ROOT}/scripts/README.md"

assert_contains() {
  local contents="$1"
  local expected="$2"

  if [[ "${contents}" != *"${expected}"* ]]; then
    printf 'expected scripts README to contain: %s\n' "${expected}" >&2
    exit 1
  fi
}

contents="$(cat "${README_PATH}")"

assert_contains "${contents}" '## Script Relationships'
assert_contains "${contents}" '```mermaid'
assert_contains "${contents}" 'not a script named `verify.sh`'
assert_contains "${contents}" 'DockerGo --> DockerRestart'
assert_contains "${contents}" 'DockerGo --> DockerCheck'
assert_contains "${contents}" 'DockerGo --> DockerAws'
assert_contains "${contents}" 'DockerRestart --> DockerStop'
assert_contains "${contents}" 'DockerRestart --> DockerStart'
assert_contains "${contents}" 'DockerFull --> DockerVerify'
assert_contains "${contents}" 'DockerFull --> DockerScan'
assert_contains "${contents}" 'PrepareRelease -.->|make release-check| ReleaseCheck'
assert_contains "${contents}" 'Update this diagram whenever a script developers run directly'

printf '%s\n' 'scripts README tests passed'
