#!/usr/bin/env bash
#
# prepare-release.sh
#
# Purpose:
#   Run the local release-preparation gates for a specific version and print
#   the manual GitHub Release steps to complete afterward.
#
# Usage:
#   ./scripts/prepare-release.sh v0.2.0
#

set -euo pipefail

SCRIPT_NAME="$(basename "$0")"

usage() {
  cat <<EOF
Usage:
  ${SCRIPT_NAME} vX.Y.Z

Example:
  ${SCRIPT_NAME} v0.2.0

Runs release checks and writes long output to:
  /tmp/local-genai-lab-release-check-vX.Y.Z.txt
  /tmp/local-genai-lab-release-check-docker-vX.Y.Z.txt

This script does not create tags or publish GitHub Releases.
EOF
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

run_step() {
  printf '\n$ %s\n' "$*"
  "$@"
}

run_logged_step() {
  local label="$1"
  local output_file="$2"
  shift 2

  printf '\n$ %s > %s 2>&1\n' "$*" "${output_file}"
  if "$@" >"${output_file}" 2>&1; then
    printf '%s passed. Output: %s\n' "${label}" "${output_file}"
    return 0
  fi

  printf '%s failed. Inspect: %s\n' "${label}" "${output_file}" >&2
  return 1
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

if [ "$#" -ne 1 ]; then
  usage >&2
  exit 1
fi

VERSION="$1"

if [[ ! "${VERSION}" =~ ^v[0-9]+[.][0-9]+[.][0-9]+([-.][0-9A-Za-z.-]+)?$ ]]; then
  die "version must look like v0.2.0"
fi

RELEASE_CHECK_OUTPUT="/tmp/local-genai-lab-release-check-${VERSION}.txt"
RELEASE_CHECK_DOCKER_OUTPUT="/tmp/local-genai-lab-release-check-docker-${VERSION}.txt"

printf 'Local GenAI Lab release preparation\n'
printf 'version: %s\n' "${VERSION}"
printf 'release check output: %s\n' "${RELEASE_CHECK_OUTPUT}"
printf 'docker release check output: %s\n' "${RELEASE_CHECK_DOCKER_OUTPUT}"

run_step git status
run_step git pull
run_logged_step "release check" "${RELEASE_CHECK_OUTPUT}" make release-check
run_logged_step "docker-inclusive release check" "${RELEASE_CHECK_DOCKER_OUTPUT}" make release-check-docker
run_step git diff --check
run_step git status

cat <<EOF

Release preparation passed.

Inspect:
  ${RELEASE_CHECK_OUTPUT}
  ${RELEASE_CHECK_DOCKER_OUTPUT}

If clean, publish GitHub Release:
  tag: ${VERSION}
  target: main
  title: local genai lab ${VERSION}

After publishing:
  git fetch --tags
  git tag --list --sort=-creatordate | head
  git status
EOF
