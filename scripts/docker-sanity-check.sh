#!/usr/bin/env bash
#
# docker-sanity-check.sh
#
# Purpose:
#   Quickly verifies that Docker is usable before running heavier Docker
#   workflows such as docker-full-check or release-check-docker.
#
# Usage:
#   ./scripts/docker-sanity-check.sh
#   DOCKER_SANITY_RUN_HELLO_WORLD=true ./scripts/docker-sanity-check.sh
#
# Required Tools:
#   - bash
#   - docker with Compose v2
#
# Exit Behavior:
#   Exits with 0 when Docker and Compose are reachable.
#   Exits non-zero with next-step guidance when Docker is unavailable.
#

set -euo pipefail

DOCKER_SANITY_RUN_HELLO_WORLD="${DOCKER_SANITY_RUN_HELLO_WORLD:-false}"

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

run_check() {
  local label="$1"
  shift

  printf 'checking: %s... ' "${label}"
  if "$@" >/dev/null 2>&1; then
    printf '%s\n' 'ok'
  else
    printf '%s\n' 'failed'
    printf '\n'
    printf 'Docker sanity check failed at: %s\n' "${label}" >&2
    printf 'Command: %s\n' "$*" >&2
    printf '\n' >&2
    printf '%s\n' \
      'Next steps:' \
      '  1. Start or restart Docker Desktop.' \
      '  2. Confirm the active context with: docker context ls' \
      '  3. On Docker Desktop, use the desktop-linux context if needed: docker context use desktop-linux' \
      '  4. Retry: ./scripts/docker-sanity-check.sh' >&2
    exit 1
  fi
}

hello_world_normalized="$(normalize_bool "${DOCKER_SANITY_RUN_HELLO_WORLD}")"

printf '%s\n' 'Docker sanity check'

if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' 'Error: docker command was not found.' >&2
  printf '%s\n' 'Install Docker Desktop, start it, then retry ./scripts/docker-sanity-check.sh.' >&2
  exit 1
fi

run_check 'Docker daemon' docker version
run_check 'Docker Compose plugin' docker compose version

if [ "${hello_world_normalized}" = 'true' ]; then
  run_check 'hello-world container' docker run --rm hello-world
else
  printf '%s\n' 'skipped: hello-world container check; set DOCKER_SANITY_RUN_HELLO_WORLD=true to run it'
fi

printf '%s\n' 'Docker sanity check passed.'
