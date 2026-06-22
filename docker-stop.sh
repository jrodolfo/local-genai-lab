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

if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' 'Error: docker was not found. Install/start Docker, then retry ./docker-stop.sh.' >&2
  exit 1
fi

printf '%s\n' 'Stopping local-genai-lab Docker Compose stack'

(
  cd "${SCRIPT_DIR}"
  docker compose down
)

printf '%s\n' 'Docker stack stopped.'
