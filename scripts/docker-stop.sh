#!/usr/bin/env bash
#
# docker-stop.sh
#
# Purpose:
#   Stops the full local-genai-lab Docker Compose stack.
#
# Usage:
#   ./scripts/docker-stop.sh
#
# Required Tools:
#   - bash
#   - docker with Compose v2
#
# Expected Output:
#   Docker Compose shutdown progress.
#
# Exit Behavior:
#   Exits with 0 on success, non-zero if Docker Compose fails.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${REPO_ROOT}/ops/lib/docker-lifecycle-common.sh"

ensure_docker_available 'docker-stop.sh'

printf '%s\n' 'Stopping local-genai-lab Docker Compose stack'

(
  cd "${REPO_ROOT}"
  printf '%s\n' 'Current Docker Compose services:'
  docker compose ps -a
  docker compose down --remove-orphans
  printf '%s\n' 'Remaining Docker Compose services after stop:'
  docker compose ps -a
)

printf '%s\n' 'Docker stack stopped. Named volumes such as qdrant_data are preserved.'
print_docker_status_command
