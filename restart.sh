#!/usr/bin/env bash
#
# restart.sh
#
# Purpose:
#   Restarts the local-genai-lab application by stopping any running instances
#   and then starting it again.
#
# Usage:
#   ./restart.sh
#
# Required Tools:
#   - bash
#
# Expected Output:
#   Status messages from stop.sh and start.sh.
#
# Exit Behavior:
#   Exits with the status of the start.sh command.
#

set -euo pipefail

# --- Execution ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bash "${SCRIPT_DIR}/stop.sh"
bash "${SCRIPT_DIR}/start.sh"
