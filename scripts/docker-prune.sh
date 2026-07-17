#!/usr/bin/env bash
#
# docker-prune.sh
#
# Purpose:
#   Frees Docker disk space by pruning unused Docker resources and build cache.
#
# Usage:
#   ./scripts/docker-prune.sh --yes
#   ./scripts/docker-prune.sh --yes --volumes
#   ./scripts/docker-prune.sh --help
#
# Required Tools:
#   - bash
#   - docker
#
# Expected Output:
#   A warning summary, Docker prune progress, and follow-up disk/status commands.
#
# Exit Behavior:
#   Exits with 0 on success.
#   Exits non-zero when confirmation is missing, an unsupported flag is used,
#   or a Docker prune command fails.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${REPO_ROOT}/ops/lib/docker-lifecycle-common.sh"

print_usage() {
  printf '%s\n' \
    'Usage:' \
    '  ./scripts/docker-prune.sh --yes' \
    '  ./scripts/docker-prune.sh --yes --volumes' \
    '  ./scripts/docker-prune.sh --help' \
    '' \
    'Options:' \
    '  --yes       required confirmation; without it the script exits without pruning.' \
    '  --volumes   also prune unused Docker volumes during docker system prune.' \
    '  --help      show this help message.' \
    '' \
    'What this removes:' \
    '  - stopped containers' \
    '  - unused images' \
    '  - unused networks' \
    '  - unused build cache' \
    '  - optionally unused volumes' \
    '' \
    'Notes:' \
    '  - this may force future Docker image pulls and rebuilds' \
    '  - run ./scripts/docker-stop.sh first for maximum cleanup'
}

ensure_docker_available 'docker-prune.sh'

confirm='false'
include_volumes='false'

while [ "$#" -gt 0 ]; do
  case "$1" in
    --yes)
      confirm='true'
      ;;
    --volumes)
      include_volumes='true'
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

if [ "${confirm}" != 'true' ]; then
  printf '%s\n' \
    'Refusing to prune Docker resources without explicit confirmation.' \
    'Re-run with: ./scripts/docker-prune.sh --yes' \
    '' >&2
  print_usage >&2
  exit 1
fi

printf '%s\n' \
  'Docker prune will remove unused Docker resources on this machine.' \
  'This may force future image pulls and rebuilds.' \
  "Unused volumes: $([ "${include_volumes}" = 'true' ] && printf '%s' 'included' || printf '%s' 'not included')" \
  ''

printf '%s\n' 'Docker disk usage before prune:'
docker system df
printf '%s\n' ''

if [ "${include_volumes}" = 'true' ]; then
  printf '%s\n' 'Running: docker system prune -a --volumes --force'
  docker system prune -a --volumes --force
else
  printf '%s\n' 'Running: docker system prune -a --force'
  docker system prune -a --force
fi

printf '%s\n' '' 'Running: docker builder prune -a --force'
docker builder prune -a --force

printf '%s\n' '' 'Docker disk usage after prune:'
docker system df

printf '%s\n' \
  '' \
  'Docker prune completed.' \
  'Follow-up:' \
  '  ./scripts/docker-status.sh' \
  '  docker system df'
