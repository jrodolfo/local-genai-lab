#!/usr/bin/env bash
#
# docker-scan.sh
#
# Purpose:
#   Runs a Trivy vulnerability scan against the Docker images used by the full
#   local-genai-lab Docker Compose workflow.
#
# Usage:
#   ./docker-scan.sh
#   DOCKER_SCAN_STRICT=true ./docker-scan.sh
#
# Important Environment:
#   DOCKER_SCAN_SEVERITY sets the reported severities. Default: HIGH,CRITICAL.
#   DOCKER_SCAN_STRICT=true makes Trivy return non-zero for matching findings.
#   DOCKER_SCAN_INCLUDE_QDRANT=false skips scanning the external Qdrant image.
#   DOCKER_SCAN_BACKEND_IMAGE overrides the backend image name.
#   DOCKER_SCAN_FRONTEND_IMAGE overrides the frontend image name.
#   DOCKER_SCAN_QDRANT_IMAGE overrides the Qdrant image name.
#
# Required Tools:
#   - bash
#   - trivy
#
# Expected Output:
#   Trivy scan output for each configured image.
#
# Exit Behavior:
#   Advisory mode exits 0 unless Trivy itself cannot run.
#   Strict mode exits non-zero when Trivy finds configured-severity issues.
#

set -euo pipefail

if ! command -v trivy >/dev/null 2>&1; then
  printf '%s\n' \
    'Error: trivy was not found.' \
    'Install Trivy, then retry ./docker-scan.sh.' \
    'macOS: brew install trivy' \
    'Docs: https://aquasecurity.github.io/trivy/' >&2
  exit 1
fi

DOCKER_SCAN_SEVERITY="${DOCKER_SCAN_SEVERITY:-HIGH,CRITICAL}"
DOCKER_SCAN_STRICT="${DOCKER_SCAN_STRICT:-false}"
DOCKER_SCAN_INCLUDE_QDRANT="${DOCKER_SCAN_INCLUDE_QDRANT:-true}"
DOCKER_SCAN_BACKEND_IMAGE="${DOCKER_SCAN_BACKEND_IMAGE:-local-genai-lab-backend}"
DOCKER_SCAN_FRONTEND_IMAGE="${DOCKER_SCAN_FRONTEND_IMAGE:-local-genai-lab-frontend}"
DOCKER_SCAN_QDRANT_IMAGE="${DOCKER_SCAN_QDRANT_IMAGE:-qdrant/qdrant:v1.14.1}"

exit_code='0'
trivy_exit_code='0'

if [ "${DOCKER_SCAN_STRICT}" = 'true' ]; then
  trivy_exit_code='1'
fi

scan_image() {
  local image="$1"

  printf '%s\n' "Scanning Docker image: ${image}"
  if ! trivy image \
    --severity "${DOCKER_SCAN_SEVERITY}" \
    --ignore-unfixed \
    --exit-code "${trivy_exit_code}" \
    "${image}"; then
    exit_code='1'
  fi
}

printf '%s\n' \
  'Docker security scan' \
  "severity: ${DOCKER_SCAN_SEVERITY}" \
  "mode: $([ "${DOCKER_SCAN_STRICT}" = 'true' ] && printf '%s' 'strict' || printf '%s' 'advisory')" \
  ''

scan_image "${DOCKER_SCAN_BACKEND_IMAGE}"
scan_image "${DOCKER_SCAN_FRONTEND_IMAGE}"

if [ "${DOCKER_SCAN_INCLUDE_QDRANT}" = 'true' ]; then
  scan_image "${DOCKER_SCAN_QDRANT_IMAGE}"
else
  printf '%s\n' 'Skipping Qdrant image scan because DOCKER_SCAN_INCLUDE_QDRANT=false.'
fi

if [ "${exit_code}" != '0' ]; then
  printf '%s\n' \
    '' \
    'Docker security scan found configured-severity issues in strict mode.'
else
  printf '%s\n' \
    '' \
    'Docker security scan completed.'
fi

exit "${exit_code}"
