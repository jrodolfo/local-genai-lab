#!/usr/bin/env bash
#
# docker-status.sh
#
# Purpose:
#   Shows the Docker Compose service status for local-genai-lab.
#
# Usage:
#   ./scripts/docker-status.sh
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
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${REPO_ROOT}/ops/lib/docker-lifecycle-common.sh"

ensure_docker_available 'docker-status.sh'

next_actions=()

add_next_action() {
  local message="$1"
  local existing

  if [ "${#next_actions[@]}" -gt 0 ]; then
    for existing in "${next_actions[@]}"; do
      if [ "${existing}" = "${message}" ]; then
        return 0
      fi
    done
  fi

  next_actions+=("${message}")
}

check_http_endpoint() {
  local label="$1"
  local url="$2"
  local next_action="$3"

  if curl -fsS --connect-timeout 2 --max-time 5 "${url}" >/dev/null 2>&1; then
    printf '%s\n' "${label}: ok"
  else
    printf '%s\n' "${label}: unavailable"
    add_next_action "${next_action}"
  fi
}

printf '%s\n' 'local-genai-lab Docker Compose status'

(
  cd "${REPO_ROOT}"
  docker compose ps
)

printf '%s\n' 'Readiness:'
check_http_endpoint '  backend health' 'http://localhost:8080/actuator/health' 'inspect backend logs with: docker compose logs -f backend'
check_http_endpoint '  frontend http' 'http://localhost:3000' 'inspect frontend logs with: docker compose logs -f frontend'
check_http_endpoint '  qdrant http' 'http://localhost:6333' 'inspect qdrant logs with: docker compose logs -f qdrant'

if [ "${#next_actions[@]}" -gt 0 ]; then
  printf '%s\n' 'Next actions:'
  for next_action in "${next_actions[@]}"; do
    printf '%s\n' "  - ${next_action}"
  done
fi

print_docker_urls
print_docker_log_commands
print_docker_port_checks
print_docker_free_ports_guidance
