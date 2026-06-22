#!/usr/bin/env bash
#
# docker-restart.sh
#
# Purpose:
#   Restarts the full local-genai-lab Docker Compose stack.
#
# Usage:
#   ./docker-restart.sh
#
# Required Tools:
#   - bash
#   - docker with Compose v2
#
# Expected Output:
#   Output from docker-stop.sh followed by docker-start.sh.
#
# Exit Behavior:
#   Exits with the first non-zero status from stop or start.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"${SCRIPT_DIR}/docker-stop.sh"
"${SCRIPT_DIR}/docker-start.sh"
