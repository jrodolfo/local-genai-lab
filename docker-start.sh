#!/usr/bin/env bash
#
# docker-start.sh
#
# Purpose:
#   Starts the full local-genai-lab Docker Compose stack: backend, frontend,
#   and Qdrant.
#
# Usage:
#   ./docker-start.sh
#
# Required Tools:
#   - bash
#   - docker with Compose v2
#
# Expected Output:
#   Docker Compose startup progress and the expected service URLs.
#
# Exit Behavior:
#   Exits with 0 on success, non-zero if Docker Compose fails.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' 'Error: docker was not found. Install/start Docker, then retry ./docker-start.sh.' >&2
  exit 1
fi

printf '%s\n' 'Starting local-genai-lab with Docker Compose'
printf '%s\n' 'note: keep Ollama running on the host for the default Ollama provider.'

(
  cd "${SCRIPT_DIR}"
  docker compose up -d --build
)

printf '%s\n' \
  'Docker stack started.' \
  '  frontend: http://localhost:3000' \
  '  backend: http://localhost:8080' \
  '  qdrant: http://localhost:6333'
