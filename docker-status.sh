#!/usr/bin/env bash
#
# docker-status.sh
#
# Purpose:
#   Shows the Docker Compose service status for local-genai-lab.
#
# Usage:
#   ./docker-status.sh
#
# Required Tools:
#   - bash
#   - docker with Compose v2
#
# Expected Output:
#   Docker Compose service table and expected service URLs.
#
# Exit Behavior:
#   Exits with 0 on success, non-zero if Docker Compose fails.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' 'Error: docker was not found. Install/start Docker, then retry ./docker-status.sh.' >&2
  exit 1
fi

printf '%s\n' 'local-genai-lab Docker Compose status'

(
  cd "${SCRIPT_DIR}"
  docker compose ps
)

printf '%s\n' \
  'expected URLs:' \
  '  frontend: http://localhost:3000' \
  '  backend: http://localhost:8080' \
  '  qdrant: http://localhost:6333'
