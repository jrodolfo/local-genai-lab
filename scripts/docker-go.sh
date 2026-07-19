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
  if [ "${label}" = 'verify Docker AWS identity' ]; then
    printf '%s\n' \
      'Docker go stopped because AWS identity verification failed.' \
      'The Docker stack is still running, but AWS-backed Agent tools are not ready.' \
      '' \
      'You can still open the UI, but AWS-backed Agent tools may not work.' \
      '' \
      'Inspect:' \
      '  ./scripts/docker-status.sh' \
      '  ./scripts/docker-aws-preflight.sh' \
      '  docker exec llm-backend aws sts get-caller-identity' >&2
  else
    printf '%s\n' 'Docker go stopped before the deployment was ready for testing.' >&2
  fi
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
  'Where to test' \
  '' \
  'If Docker runs locally on your Mac or Windows computer:' \
  '  1. Run docker-go on that computer.' \
  '  2. Open http://localhost:3000.' \
  '  3. Do not create an SSH tunnel.' \
  '' \
  'If Docker runs on EC2 or another remote host:' \
  '  1. Run docker-go on the remote Docker host.' \
  '  2. On your Mac or workstation, run and leave open:' \
  '     ssh -N -L 3001:localhost:3000 <ssh-host>' \
  '  3. Open http://localhost:3001 on your Mac or workstation.' \
  '' \
  'Replace <ssh-host> with an SSH alias, user@host name, or user@IP address.' \
  'After frontend changes, use an Incognito window or DevTools Empty Cache and Hard Reload.'
