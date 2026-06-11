#!/usr/bin/env bash
#
# restart.sh
#
# Purpose:
#   Restarts the local-genai-lab application by stopping managed processes and
#   configured port owners, then starting it again.
#
# Usage:
#   ./restart.sh
#
# Important Environment:
#   Pass the same variables accepted by start.sh, for example:
#   RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./restart.sh
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

bash "${SCRIPT_DIR}/stop.sh" --all
bash "${SCRIPT_DIR}/start.sh"
