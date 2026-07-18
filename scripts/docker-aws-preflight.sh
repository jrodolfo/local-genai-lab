#!/usr/bin/env bash
#
# docker-aws-preflight.sh
#
# Purpose:
#   Verifies that Docker-based AWS Agent tools can authenticate from the running
#   backend container before a user starts an AWS Agent test.
#
# Usage:
#   ./scripts/docker-aws-preflight.sh
#
# Required Tools:
#   - bash
#   - docker
#
# Expected Output:
#   Pass/fail lines for the AWS tools configuration, host mount source, backend
#   container, mounted AWS directory, AWS CLI, jq, and STS identity.
#
# Exit Behavior:
#   Exits with 0 only when the running backend container can authenticate with
#   AWS STS. This command is read-only and never prints credential values.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=ops/lib/docker-lifecycle-common.sh
source "${REPO_ROOT}/ops/lib/docker-lifecycle-common.sh"

check() {
  local label="$1"
  local failure_message="$2"
  shift 2

  printf 'checking: %s... ' "${label}"
  if "$@" >/dev/null 2>&1; then
    printf '%s\n' 'pass'
    return 0
  fi

  printf '%s\n' 'fail'
  printf '%s\n' "${failure_message}" >&2
  exit 1
}

has_aws_configuration_material() {
  [ -f "${LOCAL_GENAI_LAB_AWS_DIR}/config" ] || [ -f "${LOCAL_GENAI_LAB_AWS_DIR}/credentials" ]
}

backend_container_is_running() {
  [ "$(docker inspect --format '{{.State.Running}}' llm-backend 2>/dev/null)" = 'true' ]
}

ensure_docker_available 'docker-aws-preflight.sh'
load_docker_aws_tools_env

printf '%s\n' 'Docker AWS preflight'

if [ "${LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS}" != 'true' ]; then
  printf '%s\n' \
    'fail: Docker AWS tools are disabled.' \
    'Create .env.docker-aws-tools from .env.docker-aws-tools.example, set LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true, then restart Docker.' >&2
  exit 1
fi

printf 'configured profile: %s\n' "${AWS_PROFILE:-default}"
printf 'configured region: %s\n' "${AWS_REGION:-${AWS_DEFAULT_REGION:-not set}}"

check \
  'AWS configuration directory on host' \
  "Error: LOCAL_GENAI_LAB_AWS_DIR is unavailable: ${LOCAL_GENAI_LAB_AWS_DIR}. Verify the path in .env.docker-aws-tools, then restart Docker." \
  test -d "${LOCAL_GENAI_LAB_AWS_DIR}"

check \
  'AWS configuration material on host' \
  "Error: ${LOCAL_GENAI_LAB_AWS_DIR} contains neither config nor credentials. Configure AWS locally, then retry." \
  has_aws_configuration_material

check \
  'backend container is running' \
  'Error: llm-backend is not running. Run ./scripts/docker-restart.sh, then retry.' \
  backend_container_is_running

check \
  'AWS directory is mounted in backend' \
  'Error: /root/.aws is unavailable in llm-backend. Verify LOCAL_GENAI_LAB_AWS_DIR and restart Docker so the AWS Compose override is applied.' \
  docker exec llm-backend sh -lc 'test -d /root/.aws && { test -f /root/.aws/config || test -f /root/.aws/credentials; }'

check \
  'AWS CLI and jq in backend' \
  'Error: aws or jq is unavailable in llm-backend. Rebuild with ./scripts/build.sh and restart with ./scripts/docker-restart.sh.' \
  docker exec llm-backend sh -lc 'command -v aws >/dev/null && command -v jq >/dev/null'

printf 'checking: AWS STS identity... '
if identity="$(docker exec llm-backend aws sts get-caller-identity --query '[Account, Arn]' --output text 2>/dev/null)"; then
  IFS=$'\t' read -r account arn <<<"${identity}"
  if [ -n "${account}" ] && [ -n "${arn}" ]; then
    printf '%s\n' 'pass'
    printf '%s\n' 'AWS authentication is ready:'
    printf 'account: %s\n' "${account}"
    printf 'arn: %s\n' "${arn}"
  else
    printf '%s\n' 'fail'
    printf '%s\n' 'Error: AWS STS returned an incomplete identity in llm-backend.' >&2
    exit 1
  fi
else
  printf '%s\n' 'fail'
  printf '%s\n' \
    'Error: AWS STS authentication failed in llm-backend.' \
    'Verify AWS_PROFILE, credential validity, region settings, and IAM access. For details, run: docker exec llm-backend aws sts get-caller-identity' >&2
  exit 1
fi

printf '%s\n' 'Docker AWS preflight passed. Agent AWS tools can use the mounted AWS identity.'
