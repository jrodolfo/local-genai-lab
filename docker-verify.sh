#!/usr/bin/env bash
#
# docker-verify.sh
#
# Purpose:
#   Runs the full Docker verification workflow for local-genai-lab. This is not
#   read-only: it stops host-run processes, restarts the Docker Compose stack,
#   prints Docker status, and runs the Docker smoke check.
#
# Usage:
#   ./docker-verify.sh
#
# Required Tools:
#   - bash
#   - docker with Compose v2
#   - curl
#
# Expected Output:
#   Output from stop.sh, docker-restart.sh, docker-status.sh, and
#   docker-check.sh, plus a final success message when all steps pass.
#
# Exit Behavior:
#   Exits with the first non-zero status from any step.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

printf '%s\n' \
  'Docker verification will:' \
  '  1. stop host-run backend/frontend processes' \
  '  2. restart the Docker Compose stack' \
  '  3. show Docker Compose status and readiness' \
  '  4. run Docker smoke checks' \
  ''

"${SCRIPT_DIR}/stop.sh" --all
"${SCRIPT_DIR}/docker-restart.sh"
"${SCRIPT_DIR}/docker-status.sh"
"${SCRIPT_DIR}/docker-check.sh"

printf '%s\n' \
  '' \
  'Docker verification completed successfully.'
