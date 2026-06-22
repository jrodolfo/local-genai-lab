#!/usr/bin/env bash
#
# docker-check.sh
#
# Purpose:
#   Runs a read-only smoke check against an already-running Docker Compose
#   stack. This validates that the containerized app is usable enough for demos
#   or manual testing.
#
# Usage:
#   ./docker-check.sh
#
# Required Tools:
#   - bash
#   - curl
#
# Expected Output:
#   Pass/fail lines for backend, frontend, Qdrant, models API, and RAG status.
#
# Exit Behavior:
#   Exits with 0 when all checks pass, 1 when any check fails.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${SCRIPT_DIR}/ops/lib/docker-lifecycle-common.sh"

if ! command -v curl >/dev/null 2>&1; then
  printf '%s\n' 'Error: curl was not found. Install curl, then retry ./docker-check.sh.' >&2
  exit 1
fi

failed=false

run_check() {
  local name="$1"
  local url="$2"
  local log_command="$3"

  if curl -fsS --connect-timeout 2 --max-time 8 "${url}" >/dev/null 2>&1; then
    printf '%s\n' "pass: ${name}"
  else
    printf '%s\n' "fail: ${name}"
    printf '%s\n' "  url: ${url}"
    printf '%s\n' "  logs: ${log_command}"
    failed=true
  fi
}

printf '%s\n' 'Checking local-genai-lab Docker Compose stack'

run_check 'backend health' 'http://localhost:8080/actuator/health' 'docker compose logs -f backend'
run_check 'frontend http' 'http://localhost:3000' 'docker compose logs -f frontend'
run_check 'qdrant http' 'http://localhost:6333' 'docker compose logs -f qdrant'
run_check 'backend models api' 'http://localhost:8080/api/models' 'docker compose logs -f backend'
run_check 'backend rag status api' 'http://localhost:8080/api/rag/status' 'docker compose logs -f backend'

if [ "${failed}" = 'true' ]; then
  printf '%s\n' \
    '' \
    'Docker smoke check failed.' \
    'Run ./docker-status.sh for Compose status, readiness, logs, and port diagnostics.'
  exit 1
fi

printf '%s\n' \
  '' \
  'Docker smoke check passed.' \
  'The Docker stack is responding on the expected local endpoints.'
