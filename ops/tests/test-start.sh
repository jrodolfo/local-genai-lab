#!/usr/bin/env bash
#
# test-start.sh
#
# Purpose:
#   Unit tests for scripts/start.sh preflight behavior. Verifies strict Qdrant
#   startup for explicit Qdrant mode and best-effort startup for RAG comparison.
#
# Usage:
#   ./ops/tests/test-start.sh
#
# Required Tools:
#   - bash
#   - mktemp
#
# Expected Output:
#   Success message: "start tests passed"
#   Failure message and non-zero exit code if assertions fail.
#
# Exit Behavior:
#   Exits with 0 on success, 1 on any test failure.
#

set -euo pipefail

# --- Path Definitions ---
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="${REPO_ROOT}/scripts/start.sh"

# --- Test Helpers ---

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

assert_file_exists() {
  local path="$1"
  if [ ! -f "${path}" ]; then
    printf 'expected file to exist: %s\n' "${path}" >&2
    exit 1
  fi
}

assert_file_missing() {
  local path="$1"
  if [ -f "${path}" ]; then
    printf 'expected file to be missing: %s\ncontents:\n%s\n' "${path}" "$(cat "${path}")" >&2
    exit 1
  fi
}

write_mock_docker() {
  local bin_dir="$1"

  cat >"${bin_dir}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${MOCK_DOCKER_LOG}"
if [ "${MOCK_DOCKER_FAIL:-false}" = "true" ]; then
  exit 1
fi
EOF

  chmod +x "${bin_dir}/docker"
}

link_required_tool() {
  local bin_dir="$1"
  local tool="$2"
  local bash_path tool_path

  bash_path="$(type -P bash)"
  tool_path="$(type -P "${tool}" || true)"

  {
    printf '#!%s\n' "${bash_path}"
    if [ -n "${tool_path}" ]; then
      printf 'exec %q "$@"\n' "${tool_path}"
    else
      printf '%s "$@"\n' "${tool}"
    fi
  } >"${bin_dir}/${tool}"
  chmod +x "${bin_dir}/${tool}"
}

run_start() {
  local tmp_dir="$1"
  shift
  local env_file="${tmp_dir}/test.env"
  local start_path="${START_TEST_PATH:-${tmp_dir}/bin:/usr/bin:/bin}"
  : >"${env_file}"

  env -i \
    HOME="${HOME:-}" \
    PATH="${start_path}" \
    ENV_FILE="${env_file}" \
    RUN_DIR="${tmp_dir}/run" \
    START_DRY_RUN=true \
    MOCK_DOCKER_LOG="${tmp_dir}/docker.log" \
    "$@" \
    bash "${SCRIPT_PATH}"
}

# --- Test Cases ---

test_lexical_mode_auto_starts_qdrant_for_comparison() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_docker "${tmp_dir}/bin"

  output="$(run_start "${tmp_dir}" RAG_RETRIEVAL_MODE=lexical)"

  assert_contains "${output}" 'qdrant: starting via docker compose'
  assert_contains "${output}" 'Start dry run complete.'
  assert_file_exists "${tmp_dir}/docker.log"
  assert_contains "$(cat "${tmp_dir}/docker.log")" 'compose up -d qdrant'
  rm -rf "${tmp_dir}"
}

test_in_memory_vector_mode_auto_starts_qdrant_for_comparison() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_docker "${tmp_dir}/bin"

  output="$(run_start "${tmp_dir}" RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=in-memory)"

  assert_contains "${output}" 'qdrant: starting via docker compose'
  assert_contains "${output}" 'Start dry run complete.'
  assert_file_exists "${tmp_dir}/docker.log"
  assert_contains "$(cat "${tmp_dir}/docker.log")" 'compose up -d qdrant'
  rm -rf "${tmp_dir}"
}

test_qdrant_auto_start_can_be_disabled() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_docker "${tmp_dir}/bin"

  output="$(run_start "${tmp_dir}" RAG_RETRIEVAL_MODE=lexical RAG_QDRANT_AUTO_START=false)"

  assert_contains "${output}" 'Start dry run complete.'
  assert_file_missing "${tmp_dir}/docker.log"
  rm -rf "${tmp_dir}"
}

test_qdrant_vector_mode_starts_qdrant() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_docker "${tmp_dir}/bin"

  output="$(run_start "${tmp_dir}" RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant)"

  assert_contains "${output}" 'qdrant: starting via docker compose'
  assert_contains "${output}" 'Start dry run complete.'
  assert_file_exists "${tmp_dir}/docker.log"
  assert_contains "$(cat "${tmp_dir}/docker.log")" 'compose up -d qdrant'
  rm -rf "${tmp_dir}"
}

test_qdrant_vector_mode_requires_docker() {
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  link_required_tool "${tmp_dir}/bin" bash
  link_required_tool "${tmp_dir}/bin" dirname
  link_required_tool "${tmp_dir}/bin" pwd
  link_required_tool "${tmp_dir}/bin" mkdir
  link_required_tool "${tmp_dir}/bin" tr

  if START_TEST_PATH="${tmp_dir}/bin" run_start "${tmp_dir}" RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant >/tmp/start-qdrant-missing.out 2>/tmp/start-qdrant-missing.err; then
    printf 'expected qdrant mode without docker to fail\n' >&2
    exit 1
  fi

  assert_contains "$(cat /tmp/start-qdrant-missing.err)" 'RAG_VECTOR_STORE=qdrant requires Docker'
  rm -f /tmp/start-qdrant-missing.out /tmp/start-qdrant-missing.err
  rm -rf "${tmp_dir}"
}

test_optional_qdrant_auto_start_without_docker_continues() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  link_required_tool "${tmp_dir}/bin" bash
  link_required_tool "${tmp_dir}/bin" dirname
  link_required_tool "${tmp_dir}/bin" pwd
  link_required_tool "${tmp_dir}/bin" mkdir
  link_required_tool "${tmp_dir}/bin" tr

  output="$(START_TEST_PATH="${tmp_dir}/bin" run_start "${tmp_dir}" RAG_RETRIEVAL_MODE=lexical 2>&1)"

  assert_contains "${output}" 'Warning: Qdrant auto-start skipped because docker was not found.'
  assert_contains "${output}" 'Start dry run complete.'
  rm -rf "${tmp_dir}"
}

test_qdrant_vector_mode_reports_docker_failure() {
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_docker "${tmp_dir}/bin"

  if run_start "${tmp_dir}" RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant MOCK_DOCKER_FAIL=true >/tmp/start-qdrant-fail.out 2>/tmp/start-qdrant-fail.err; then
    printf 'expected qdrant mode with failing docker to fail\n' >&2
    exit 1
  fi

  assert_contains "$(cat /tmp/start-qdrant-fail.err)" 'failed to start Qdrant with docker compose'
  rm -f /tmp/start-qdrant-fail.out /tmp/start-qdrant-fail.err
  rm -rf "${tmp_dir}"
}

# --- Main ---

main() {
  test_lexical_mode_auto_starts_qdrant_for_comparison
  test_in_memory_vector_mode_auto_starts_qdrant_for_comparison
  test_qdrant_auto_start_can_be_disabled
  test_qdrant_vector_mode_starts_qdrant
  test_qdrant_vector_mode_requires_docker
  test_optional_qdrant_auto_start_without_docker_continues
  test_qdrant_vector_mode_reports_docker_failure
  printf 'start tests passed\n'
}

main "$@"
