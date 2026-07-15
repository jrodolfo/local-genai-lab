#!/usr/bin/env bash
#
# docker-logs.sh
#
# Purpose:
#   Follows Docker Compose logs for all local-genai-lab services or one named
#   service.
#
# Usage:
#   ./scripts/docker-logs.sh
#   ./scripts/docker-logs.sh backend
#   ./scripts/docker-logs.sh frontend
#   ./scripts/docker-logs.sh qdrant
#
# Required Tools:
#   - bash
#   - docker with Compose v2
#
# Exit Behavior:
#   Exits with 0 on success, non-zero if Docker Compose fails.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${REPO_ROOT}/ops/lib/docker-lifecycle-common.sh"

usage() {
  cat <<EOF
Usage:
  ./scripts/docker-logs.sh [backend|frontend|qdrant]

With no service argument, follows logs for all Docker Compose services.
EOF
}

ensure_docker_available 'docker-logs.sh'

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

if [ "$#" -gt 1 ]; then
  usage >&2
  exit 1
fi

case "${1:-}" in
  '')
    cd "${REPO_ROOT}"
    docker_compose logs -f
    ;;
  backend|frontend|qdrant)
    cd "${REPO_ROOT}"
    docker_compose logs -f "$1"
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
