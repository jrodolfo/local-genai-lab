#!/usr/bin/env bash
#
# build.sh
#
# Purpose:
#   Builds local-genai-lab artifacts without starting or stopping the app.
#   Run this before ./restart.sh when you want a fresh backend/frontend/MCP
#   build from the current source tree.
#
# Usage:
#   ./build.sh [--skip-tests] [--clean-frontend]
#
# Options:
#   --skip-tests       Build the backend package without running backend tests.
#                      By default, backend tests run during the Maven package.
#   --clean-frontend   Remove frontend/dist before building the frontend.
#   --help             Show usage.
#
# Environment:
#   BUILD_DRY_RUN=true prints the commands that would run without executing
#   them. This is intended for ops tests and command review.
#
# Required Tools:
#   - bash
#   - mvn
#   - npm
#
# Exit Behavior:
#   Exits with 0 on success, non-zero on the first failed build command.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${SCRIPT_DIR}"

SKIP_TESTS=false
CLEAN_FRONTEND=false
BUILD_DRY_RUN="${BUILD_DRY_RUN:-false}"

usage() {
  sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --skip-tests)
      SKIP_TESTS=true
      ;;
    --clean-frontend)
      CLEAN_FRONTEND=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf '%s\n' "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

print_step() {
  printf '\n%s\n' "==> $1"
}

run_cmd() {
  printf '+'
  printf ' %q' "$@"
  printf '\n'
  if [ "${BUILD_DRY_RUN}" = "true" ]; then
    return 0
  fi
  "$@"
}

run_in_dir() {
  local dir="$1"
  shift
  printf '+ cd %q &&' "${dir}"
  printf ' %q' "$@"
  printf '\n'
  if [ "${BUILD_DRY_RUN}" = "true" ]; then
    return 0
  fi
  (
    cd "${dir}"
    "$@"
  )
}

ensure_node_dependencies() {
  local dir="$1"
  local label="$2"

  if [ -d "${dir}/node_modules" ]; then
    printf '%s\n' "${label} dependencies already installed."
    return 0
  fi

  print_step "Install ${label} dependencies"
  run_in_dir "${dir}" npm install
}

print_step "Build local-genai-lab"
printf '%s\n' \
  "repo_root=${REPO_ROOT}" \
  "skip_tests=${SKIP_TESTS}" \
  "clean_frontend=${CLEAN_FRONTEND}" \
  "dry_run=${BUILD_DRY_RUN}"

print_step "Build backend"
if [ "${SKIP_TESTS}" = "true" ]; then
  run_in_dir "${REPO_ROOT}/backend" mvn clean package -DskipTests
else
  run_in_dir "${REPO_ROOT}/backend" mvn clean package
fi

ensure_node_dependencies "${REPO_ROOT}/frontend" "frontend"

if [ "${CLEAN_FRONTEND}" = "true" ]; then
  print_step "Clean frontend build output"
  run_cmd rm -rf "${REPO_ROOT}/frontend/dist"
fi

print_step "Build frontend"
run_in_dir "${REPO_ROOT}/frontend" npm run build

if [ -f "${REPO_ROOT}/mcp/package.json" ]; then
  ensure_node_dependencies "${REPO_ROOT}/mcp" "MCP"
  print_step "Build MCP server"
  run_in_dir "${REPO_ROOT}/mcp" npm run build
fi

printf '\n%s\n' 'Build completed successfully.'
