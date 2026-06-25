#!/usr/bin/env bash
#
# docker-full-check.sh
#
# Purpose:
#   Runs the broadest local Docker validation workflow: functional Docker
#   verification followed by an advisory Docker image security scan.
#
# Usage:
#   ./docker-full-check.sh
#   DOCKER_SCAN_STRICT=true ./docker-full-check.sh
#
# Required Tools:
#   - bash
#   - docker with Compose v2
#   - curl
#   - trivy
#
# Expected Output:
#   Output from docker-verify.sh followed by docker-scan.sh, plus a final
#   success message when both steps pass.
#
# Exit Behavior:
#   Exits with the first non-zero status from docker-verify.sh or
#   docker-scan.sh.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

printf '%s\n' \
  'Docker full check will:' \
  '  1. run Docker functional verification' \
  '  2. run Docker image security scan' \
  ''

"${SCRIPT_DIR}/docker-verify.sh"
"${SCRIPT_DIR}/docker-scan.sh"

printf '%s\n' \
  '' \
  'Docker full check completed successfully.'
