#!/usr/bin/env bash
#
# docker-start.sh
#
# Purpose:
#   Starts the full local-genai-lab Docker Compose stack: backend, frontend,
#   and Qdrant.
#
# Usage:
#   ./scripts/docker-start.sh
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
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${REPO_ROOT}/ops/lib/docker-lifecycle-common.sh"

ensure_docker_available 'docker-start.sh'

printf '%s\n' 'Starting local-genai-lab with Docker Compose'
printf '%s\n' 'note: keep Ollama running on the host for the default Ollama provider.'

if ! (
  cd "${REPO_ROOT}"
  docker_compose up -d --build
); then
  print_docker_start_failure_summary >&2
  exit 1
fi

printf '%s\n' 'Docker stack started.'
print_docker_runtime_summary
