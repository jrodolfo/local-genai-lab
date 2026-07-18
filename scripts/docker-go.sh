#!/usr/bin/env bash
#
# docker-go.sh
#
# Purpose:
#   Prepares a Docker deployment for a trustworthy AWS Agent test. By default
#   it builds local artifacts, restarts Docker, smoke-checks the stack, and
#   verifies the AWS identity mounted in the backend container.
#
# Usage:
#   ./scripts/docker-go.sh
#   ./scripts/docker-go.sh --skip-build
#
# Options:
#   --skip-build  Restart and validate the current Docker deployment without
#                 running the local build first.
#   --help        Show usage.
#
# Exit Behavior:
#   Exits with 0 only after every selected preparation step passes. Stops at
#   the first failure so later UI testing does not use an unverified deployment.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
skip_build=false

usage() {
  sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
}

run_step() {
  local number="$1"
  local label="$2"
  shift 2

  printf '\n%s\n' "==> ${number}. ${label}"
  if "$@"; then
    printf '%s\n' "pass: ${label}"
    return 0
  else
    status=$?
  fi

  printf '%s\n' "fail: ${label}" >&2
  printf '%s\n' 'Docker go stopped before the deployment was ready for testing.' >&2
  exit "${status}"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --skip-build)
      skip_build=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Error: unsupported option: %s\n\n' "$1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

printf '%s\n' \
  'Docker Agent preparation' \
  'This workflow validates the deployment before browser testing.'

step_number=1
if [ "${skip_build}" = false ]; then
  run_step "${step_number}" 'build local artifacts' "${SCRIPT_DIR}/build.sh"
  step_number=$((step_number + 1))
else
  printf '%s\n' 'skipped: build local artifacts (--skip-build)'
fi

run_step "${step_number}" 'restart Docker Compose stack' "${SCRIPT_DIR}/docker-restart.sh"
step_number=$((step_number + 1))
run_step "${step_number}" 'smoke-check Docker Compose stack' "${SCRIPT_DIR}/docker-check.sh"
step_number=$((step_number + 1))
run_step "${step_number}" 'verify Docker AWS identity' "${SCRIPT_DIR}/docker-aws-preflight.sh"

printf '%s\n' \
  '' \
  'Docker deployment is ready for AWS Agent testing.' \
  '' \
  'For local Docker testing, open: http://localhost:3000' \
  'After frontend changes, use an Incognito window or DevTools Empty Cache and Hard Reload.'

printf '%s\n' \
  '' \
  'For remote Docker testing, run this command on your workstation and leave it open:' \
  '  ssh -N -L 3001:localhost:3000 <ssh-host>' \
  '' \
  'Replace <ssh-host> with an SSH alias, user@host name, or user@IP address.' \
  'Then test the remote deployment at: http://localhost:3001' \
  'Do not use http://localhost:3000 when a separate local Docker deployment may be running.' \
  'Do not run the SSH command on this Docker host.'
