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
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${SCRIPT_DIR}/ops/lib/docker-lifecycle-common.sh"

ensure_docker_available 'docker-status.sh'

printf '%s\n' 'local-genai-lab Docker Compose status'

(
  cd "${SCRIPT_DIR}"
  docker compose ps
)

print_docker_urls
print_docker_log_commands
print_docker_port_checks
