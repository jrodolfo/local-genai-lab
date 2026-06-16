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
if [[ "${url}" == "http://localhost:8080/api/rag/status" ]]; then
  if [ "${MOCK_BACKEND_RAG_STATUS:-up}" != "up" ]; then
    exit 1
  fi
  printf '{"enabled":%s,"retrievalMode":"%s","vectorStore":"%s","qdrantUrl":"%s","qdrantCollection":"%s","embeddingProvider":"%s","embeddingModel":"%s"}\n' \
    "${MOCK_BACKEND_RAG_ENABLED:-${RAG_ENABLED:-true}}" \
    "${MOCK_BACKEND_RAG_RETRIEVAL_MODE:-${RAG_RETRIEVAL_MODE:-lexical}}" \
    "${MOCK_BACKEND_RAG_VECTOR_STORE:-${RAG_VECTOR_STORE:-in-memory}}" \
    "${MOCK_BACKEND_RAG_QDRANT_URL:-${RAG_QDRANT_URL:-http://localhost:6333}}" \
    "${MOCK_BACKEND_RAG_QDRANT_COLLECTION:-${RAG_QDRANT_COLLECTION:-local_genai_lab_docs}}" \
    "${MOCK_BACKEND_RAG_EMBEDDING_PROVIDER:-${RAG_EMBEDDING_PROVIDER:-ollama}}" \
    "${MOCK_BACKEND_RAG_EMBEDDING_MODEL:-${RAG_EMBEDDING_MODEL:-nomic-embed-text}}"
  exit 0
fi
if [[ "${url}" == "http://localhost:8080/actuator/health" ]]; then
  exit 0
fi
if [[ "${url}" == *"localhost:11434/api/tags"* && "${MOCK_OLLAMA_SERVICE:-down}" == "up" ]]; then
  exit 0
fi
if [[ "${url}" == "http://localhost:6333" && "${MOCK_QDRANT_SERVICE:-down}" == "up" ]]; then
  exit 0
fi
if [[ "${url}" == "http://localhost:6333/collections/local_genai_lab_docs" && "${MOCK_QDRANT_COLLECTION:-missing}" == "present" ]]; then
  printf '%s\n' '{"result":{"points_count":123}}'
  exit 0
fi
if [[ "${url}" == "http://localhost:6333/collections/local_genai_lab_docs" ]]; then
  exit 1
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

# test_lexical_mode_without_qdrant_auto_start_does_not_require_embedding_model
# Purpose: Verifies lightweight lexical RAG does not require embeddings when
#          optional vector comparison startup is disabled.
test_lexical_mode_without_qdrant_auto_start_does_not_require_embedding_model() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=lexical RAG_QDRANT_AUTO_START=false MOCK_OLLAMA_SERVICE=up MOCK_EMBEDDING_MODEL=missing)"

  assert_contains "${output}" 'requested rag enabled: true'
  assert_contains "${output}" 'requested rag retrieval mode: lexical'
  assert_contains "${output}" 'requested rag vector store: in-memory'
  assert_contains "${output}" 'requested rag qdrant auto-start: false'
  assert_contains "${output}" 'backend rag enabled: true'
  assert_contains "${output}" 'backend rag retrieval mode: lexical'
  assert_contains "${output}" 'backend rag vector store: in-memory'
  assert_contains "${output}" 'ollama cli: available'
  assert_contains "${output}" 'ollama service: ok'
  assert_contains "${output}" 'ollama embedding model: not required for current mode'
  rm -rf "${tmp_dir}"
}

# test_lexical_mode_reports_optional_qdrant_comparison_readiness
# Purpose: Verifies default lexical status still surfaces Qdrant readiness
#          because the UI exposes Vector - Qdrant comparison.
test_lexical_mode_reports_optional_qdrant_comparison_readiness() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=lexical MOCK_QDRANT_SERVICE=up MOCK_QDRANT_COLLECTION=present MOCK_OLLAMA_SERVICE=up MOCK_EMBEDDING_MODEL=present)"

  assert_contains "${output}" 'requested rag retrieval mode: lexical'
  assert_contains "${output}" 'requested rag qdrant auto-start: true'
  assert_contains "${output}" 'qdrant usage: optional Vector - Qdrant comparison target'
  assert_contains "${output}" 'qdrant service: ok'
  assert_contains "${output}" 'qdrant collection: present (points=123)'
  assert_contains "${output}" 'ollama embedding model: present (nomic-embed-text)'
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

  assert_contains "${output}" 'requested rag retrieval mode: vector'
  assert_contains "${output}" 'backend rag retrieval mode: vector'
  assert_contains "${output}" 'backend rag vector store: in-memory'
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

  assert_contains "${output}" 'backend rag retrieval mode: vector'
  assert_contains "${output}" 'ollama embedding model: missing (nomic-embed-text)'
  assert_contains "${output}" 'hint: run ollama pull nomic-embed-text'
  rm -rf "${tmp_dir}"
}

# test_qdrant_vector_mode_reports_reachable_service
# Purpose: Verifies Qdrant mode reports a reachable Qdrant service.
test_qdrant_vector_mode_reports_reachable_service() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant MOCK_QDRANT_SERVICE=up MOCK_QDRANT_COLLECTION=present MOCK_OLLAMA_SERVICE=up MOCK_EMBEDDING_MODEL=present)"

  assert_contains "${output}" 'requested rag retrieval mode: vector'
  assert_contains "${output}" 'requested rag vector store: qdrant'
  assert_contains "${output}" 'backend rag retrieval mode: vector'
  assert_contains "${output}" 'backend rag vector store: qdrant'
  assert_contains "${output}" 'backend rag qdrant url: http://localhost:6333'
  assert_contains "${output}" 'backend rag qdrant collection: local_genai_lab_docs'
  assert_contains "${output}" 'qdrant service: ok'
  assert_contains "${output}" 'qdrant collection: present (points=123)'
  assert_contains "${output}" 'ollama embedding model: present (nomic-embed-text)'
  rm -rf "${tmp_dir}"
}

# test_qdrant_vector_mode_reports_missing_collection
# Purpose: Verifies Qdrant mode reports a missing collection when Qdrant is reachable.
test_qdrant_vector_mode_reports_missing_collection() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant MOCK_QDRANT_SERVICE=up MOCK_QDRANT_COLLECTION=missing MOCK_OLLAMA_SERVICE=up MOCK_EMBEDDING_MODEL=present)"

  assert_contains "${output}" 'qdrant service: ok'
  assert_contains "${output}" 'qdrant collection: missing (local_genai_lab_docs)'
  rm -rf "${tmp_dir}"
}

# test_qdrant_vector_mode_reports_unavailable_service
# Purpose: Verifies Qdrant mode reports when Qdrant is unavailable.
test_qdrant_vector_mode_reports_unavailable_service() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant MOCK_QDRANT_SERVICE=down MOCK_OLLAMA_SERVICE=up MOCK_EMBEDDING_MODEL=present)"

  assert_contains "${output}" 'backend rag retrieval mode: vector'
  assert_contains "${output}" 'backend rag vector store: qdrant'
  assert_contains "${output}" 'qdrant service: unavailable (http://localhost:6333)'
  assert_contains "${output}" 'qdrant collection: not checked'
  assert_contains "${output}" 'ollama embedding model: present (nomic-embed-text)'
  rm -rf "${tmp_dir}"
}

# test_non_ollama_non_vector_config_skips_ollama_readiness
# Purpose: Verifies non-Ollama chat with lexical RAG does not require Ollama.
test_non_ollama_non_vector_config_skips_ollama_readiness() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" APP_MODEL_PROVIDER=bedrock RAG_RETRIEVAL_MODE=lexical RAG_QDRANT_AUTO_START=false MOCK_OLLAMA_SERVICE=down)"

  assert_contains "${output}" 'backend rag retrieval mode: lexical'
  assert_contains "${output}" 'ollama readiness: not required for current configuration'
  rm -rf "${tmp_dir}"
}

# test_requested_config_mismatch_reports_warning
# Purpose: Verifies status output warns when shell-requested RAG config differs from the running backend.
test_requested_config_mismatch_reports_warning() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  write_mock_commands "${tmp_dir}/bin"

  output="$(run_status "${tmp_dir}" RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant MOCK_BACKEND_RAG_RETRIEVAL_MODE=lexical MOCK_BACKEND_RAG_VECTOR_STORE=in-memory MOCK_OLLAMA_SERVICE=up)"

  assert_contains "${output}" 'requested rag retrieval mode: vector'
  assert_contains "${output}" 'requested rag vector store: qdrant'
  assert_contains "${output}" 'backend rag retrieval mode: lexical'
  assert_contains "${output}" 'backend rag vector store: in-memory'
  assert_contains "${output}" 'warning: requested RAG config differs from the running backend.'
  assert_contains "${output}" 'hint: restart with the same env values to apply it'
  rm -rf "${tmp_dir}"
}

# --- Main ---

# main
# Purpose: Entry point for the test suite.
main() {
  test_lexical_mode_without_qdrant_auto_start_does_not_require_embedding_model
  test_lexical_mode_reports_optional_qdrant_comparison_readiness
  test_vector_mode_reports_present_embedding_model
  test_vector_mode_reports_missing_embedding_model
  test_qdrant_vector_mode_reports_reachable_service
  test_qdrant_vector_mode_reports_missing_collection
  test_qdrant_vector_mode_reports_unavailable_service
  test_non_ollama_non_vector_config_skips_ollama_readiness
  test_requested_config_mismatch_reports_warning
  printf 'status tests passed\n'
}

main "$@"
