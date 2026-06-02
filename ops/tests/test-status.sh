#!/usr/bin/env bash
#
# test-status.sh
#
# Purpose:
#   Unit tests for the root status.sh script. Verifies RAG/Ollama readiness
#   output with mocked external commands so tests do not require live services.
#
# Usage:
#   ./ops/tests/test-status.sh
#
# Required Tools:
#   - bash
#   - mktemp
#
# Expected Output:
#   Success message: "status tests passed"
#   Failure message and non-zero exit code if assertions fail.
#
# Exit Behavior:
#   Exits with 0 on success, 1 on any test failure.
#

set -euo pipefail

# --- Path Definitions ---
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_PATH="${REPO_ROOT}/status.sh"

# --- Test Helpers ---

# assert_contains
# Purpose: Verifies that a string contains a specific substring.
# Inputs:
#   $1 - Haystack string to search in.
#   $2 - Needle substring to search for.
assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

# write_mock_commands
# Purpose: Creates mocked curl, lsof, and ollama commands in a temporary PATH.
# Inputs:
#   $1 - Directory where mocked commands should be written.
write_mock_commands() {
  local bin_dir="$1"

  cat >"${bin_dir}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
url="${*: -1}"
if [[ "${url}" == *"localhost:11434/api/tags"* && "${MOCK_OLLAMA_SERVICE:-down}" == "up" ]]; then
  exit 0
fi
exit 1
EOF

  cat >"${bin_dir}/lsof" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
exit 0
EOF

  cat >"${bin_dir}/ollama" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  list)
    printf '%s\n' 'NAME                       ID              SIZE      MODIFIED'
    if [ "${MOCK_EMBEDDING_MODEL:-missing}" = "present" ]; then
      printf '%s\n' 'nomic-embed-text:latest    0a109f422b47    274 MB    7 weeks ago'
      printf '%s\n' 'nomic-embed-text           0a109f422b47    274 MB    7 weeks ago'
    fi
    ;;
  *)
    ;;
esac
EOF

  chmod +x "${bin_dir}/curl" "${bin_dir}/lsof" "${bin_dir}/ollama"
}

# run_status
# Purpose: Runs status.sh with a controlled environment and mocked commands.
# Inputs:
#   $1 - Temporary directory.
#   $@ - Additional environment assignments for status.sh.
run_status() {
  local tmp_dir="$1"
  shift
  local env_file="${tmp_dir}/test.env"
  : >"${env_file}"

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:/usr/bin:/bin" \
    ENV_FILE="${env_file}" \
    "$@" \
    bash "${SCRIPT_PATH}"
}

# --- Test Cases ---

# test_lexical_mode_does_not_require_embedding_model
# Purpose: Verifies lexical RAG does not require the embedding model.
test_lexical_mode_does_not_require_embedding_model() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=lexical MOCK_OLLAMA_SERVICE=up MOCK_EMBEDDING_MODEL=missing)"

  assert_contains "${output}" 'rag enabled: true'
  assert_contains "${output}" 'rag retrieval mode: lexical'
  assert_contains "${output}" 'ollama cli: available'
  assert_contains "${output}" 'ollama service: ok'
  assert_contains "${output}" 'ollama embedding model: not required for current mode'
  rm -rf "${tmp_dir}"
}

# test_vector_mode_reports_present_embedding_model
# Purpose: Verifies vector RAG reports the configured embedding model as present.
test_vector_mode_reports_present_embedding_model() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=vector MOCK_OLLAMA_SERVICE=up MOCK_EMBEDDING_MODEL=present)"

  assert_contains "${output}" 'rag retrieval mode: vector'
  assert_contains "${output}" 'ollama service: ok'
  assert_contains "${output}" 'ollama embedding model: present (nomic-embed-text)'
  rm -rf "${tmp_dir}"
}

# test_vector_mode_reports_missing_embedding_model
# Purpose: Verifies vector RAG reports the missing model and pull hint.
test_vector_mode_reports_missing_embedding_model() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=vector MOCK_OLLAMA_SERVICE=up MOCK_EMBEDDING_MODEL=missing)"

  assert_contains "${output}" 'rag retrieval mode: vector'
  assert_contains "${output}" 'ollama embedding model: missing (nomic-embed-text)'
  assert_contains "${output}" 'hint: run ollama pull nomic-embed-text'
  rm -rf "${tmp_dir}"
}

# test_non_ollama_non_vector_config_skips_ollama_readiness
# Purpose: Verifies non-Ollama chat with lexical RAG does not require Ollama.
test_non_ollama_non_vector_config_skips_ollama_readiness() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" APP_MODEL_PROVIDER=bedrock RAG_RETRIEVAL_MODE=lexical MOCK_OLLAMA_SERVICE=down)"

  assert_contains "${output}" 'rag retrieval mode: lexical'
  assert_contains "${output}" 'ollama readiness: not required for current configuration'
  rm -rf "${tmp_dir}"
}

# --- Main ---

# main
# Purpose: Entry point for the test suite.
main() {
  test_lexical_mode_does_not_require_embedding_model
  test_vector_mode_reports_present_embedding_model
  test_vector_mode_reports_missing_embedding_model
  test_non_ollama_non_vector_config_skips_ollama_readiness
  printf 'status tests passed\n'
}

main "$@"
