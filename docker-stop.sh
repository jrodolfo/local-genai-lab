#!/usr/bin/env bash
#
# docker-stop.sh
#
# Purpose:
#   Stops the full local-genai-lab Docker Compose stack.
#
# Usage:
#   ./docker-stop.sh
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
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${SCRIPT_DIR}/ops/lib/docker-lifecycle-common.sh"

ensure_docker_available 'docker-stop.sh'

printf '%s\n' 'Stopping local-genai-lab Docker Compose stack'

(
  cd "${SCRIPT_DIR}"
  docker compose down
)

printf '%s\n' 'Docker stack stopped.'
print_docker_status_command
