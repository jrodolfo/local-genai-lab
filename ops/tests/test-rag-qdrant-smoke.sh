#!/usr/bin/env bash
#
# test-rag-qdrant-smoke.sh
#
# Purpose:
#   Optional live smoke test for the full RAG + Ollama embeddings + Qdrant path.
#   This is intentionally not part of the default test target because it needs
#   a running backend, Ollama, the embedding model, and the Qdrant service.
#
# Usage:
#   RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant ./scripts/restart.sh
#   make test-rag-qdrant-smoke
#
# Optional Environment:
#   BACKEND_URL             Backend base URL. Default: http://localhost:8080
#   RAG_SMOKE_PROVIDER      Provider override for the generated RAG answer.
#   RAG_SMOKE_MODEL         Model override for the generated RAG answer.
#   RAG_SMOKE_QUESTION      Question to ask. Default checks Java version docs.
#
# Exit Behavior:
#   Exits with 0 when the live Qdrant RAG path works, non-zero with an
#   actionable message when a dependency or API step fails.
#

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
QUESTION="${RAG_SMOKE_QUESTION:-What Java version does this project use?}"
TMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

fail() {
  printf 'rag qdrant smoke failed: %s\n' "$1" >&2
  exit 1
}

progress() {
  printf '[rag qdrant smoke] %s\n' "$1"
}

run_with_dots() {
  local label="$1"
  shift
  local status_file="${TMP_DIR}/${label}.status"
  local pid status printed_dots dot_interval
  printed_dots='false'
  dot_interval="${RAG_SMOKE_DOT_INTERVAL_SECONDS:-1}"

  (
    "$@"
    printf '%s' "$?" >"${status_file}"
  ) &
  pid="$!"

  while kill -0 "${pid}" >/dev/null 2>&1; do
    sleep "${dot_interval}"
    if kill -0 "${pid}" >/dev/null 2>&1; then
      printf '.'
      printed_dots='true'
    fi
  done
  wait "${pid}" || true
  if [ -f "${status_file}" ]; then
    status="$(cat "${status_file}")"
  else
    status=1
  fi
  if [ "${printed_dots}" = 'true' ]; then
    printf '\n'
  fi
  return "${status}"
}

require_command() {
  local command_name="$1"
  command -v "${command_name}" >/dev/null 2>&1 || fail "missing required command: ${command_name}"
}

curl_get_json() {
  local url="$1"
  local output="$2"
  curl -fsS "${url}" -o "${output}" || fail "request failed: GET ${url}"
}

curl_post_json() {
  local url="$1"
  local payload_file="$2"
  local output="$3"
  curl -fsS -X POST "${url}" \
    -H 'Content-Type: application/json' \
    --data @"${payload_file}" \
    -o "${output}" || fail "request failed: POST ${url}"
}

json_get() {
  local file="$1"
  local path="$2"
  python3 - "$file" "$path" <<'PY'
import json
import sys

file_name, path = sys.argv[1], sys.argv[2]
with open(file_name, encoding="utf-8") as handle:
    value = json.load(handle)
for part in path.split("."):
    if isinstance(value, list):
        value = value[int(part)]
    else:
        value = value.get(part)
    if value is None:
        print("")
        sys.exit(0)
if isinstance(value, bool):
    print(str(value).lower())
else:
    print(value)
PY
}

json_count() {
  local file="$1"
  local path="$2"
  python3 - "$file" "$path" <<'PY'
import json
import sys

file_name, path = sys.argv[1], sys.argv[2]
with open(file_name, encoding="utf-8") as handle:
    value = json.load(handle)
for part in path.split("."):
    if isinstance(value, list):
        value = value[int(part)]
    else:
        value = value.get(part)
    if value is None:
        print(0)
        sys.exit(0)
print(len(value) if isinstance(value, list) else 0)
PY
}

json_positive_int() {
  local file="$1"
  local path="$2"
  local label="$3"
  local value
  value="$(json_get "$file" "$path")"
  [[ "${value}" =~ ^[0-9]+$ ]] || fail "${label} was not numeric: ${value:-<empty>}"
  [ "${value}" -gt 0 ] || fail "${label} must be greater than zero"
  printf '%s' "${value}"
}

write_query_payload() {
  local output="$1"
  local provider="$2"
  local model="$3"
  python3 - "$output" "$QUESTION" "$provider" "$model" <<'PY'
import json
import sys

output, question, provider, model = sys.argv[1:5]
payload = {
    "question": question,
    "provider": provider,
    "model": model,
    "sessionId": None,
    "retrievalTarget": "vector:qdrant",
}
with open(output, "w", encoding="utf-8") as handle:
    json.dump(payload, handle)
PY
}

select_provider_and_model() {
  local models_file="$1"
  local provider_override="${RAG_SMOKE_PROVIDER:-}"
  local model_override="${RAG_SMOKE_MODEL:-}"
  python3 - "$models_file" "$provider_override" "$model_override" <<'PY'
import json
import sys

file_name, provider_override, model_override = sys.argv[1:4]
with open(file_name, encoding="utf-8") as handle:
    payload = json.load(handle)

provider = provider_override or payload.get("provider") or payload.get("defaultProvider") or ""
models = payload.get("models") or []
model = model_override or payload.get("defaultModel") or (models[0] if models else "")

if not provider:
    raise SystemExit("missing provider in /api/models payload; set RAG_SMOKE_PROVIDER")
if not model:
    raise SystemExit("missing model in /api/models payload; set RAG_SMOKE_MODEL")

print(provider)
print(model)
PY
}

check_ollama_embedding_model() {
  local embedding_model="$1"
  local escaped_model
  require_command ollama
  escaped_model="$(printf '%s' "${embedding_model}" | sed 's/[.[\*^$()+?{}|]/\\&/g')"
  ollama list | awk 'NR > 1 {print $1}' | grep -Eq "^${escaped_model}(:|$)" \
    || fail "missing Ollama embedding model ${embedding_model}; run: ollama pull ${embedding_model}"
}

main() {
  progress 'starting live smoke test'
  require_command curl
  require_command python3

  local status_file index_file models_file provider_model_file query_payload query_response qdrant_file
  local enabled retrieval_mode vector_store qdrant_url qdrant_collection qdrant_reachable embedding_provider embedding_model
  local documents chunks points provider model answer source_count

  status_file="${TMP_DIR}/rag-status.json"
  index_file="${TMP_DIR}/rag-index.json"
  models_file="${TMP_DIR}/models.json"
  provider_model_file="${TMP_DIR}/provider-model.txt"
  query_payload="${TMP_DIR}/rag-query.json"
  query_response="${TMP_DIR}/rag-query-response.json"
  qdrant_file="${TMP_DIR}/qdrant-collection.json"

  progress "checking backend health at ${BACKEND_URL}"
  curl_get_json "${BACKEND_URL}/actuator/health" "${TMP_DIR}/health.json"
  progress 'checking backend RAG configuration'
  curl_get_json "${BACKEND_URL}/api/rag/status" "${status_file}"

  enabled="$(json_get "${status_file}" enabled)"
  retrieval_mode="$(json_get "${status_file}" retrievalMode)"
  vector_store="$(json_get "${status_file}" vectorStore)"
  qdrant_url="$(json_get "${status_file}" qdrantUrl)"
  qdrant_collection="$(json_get "${status_file}" qdrantCollection)"
  qdrant_reachable="$(json_get "${status_file}" qdrantReachable)"
  embedding_provider="$(json_get "${status_file}" embeddingProvider)"
  embedding_model="$(json_get "${status_file}" embeddingModel)"

  [ "${enabled}" = "true" ] || fail "RAG is disabled; restart with RAG_ENABLED=true"
  [ "${retrieval_mode}" = "vector" ] || fail "backend retrieval mode is ${retrieval_mode}; restart with RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant"
  [ "${vector_store}" = "qdrant" ] || fail "backend vector store is ${vector_store}; restart with RAG_RETRIEVAL_MODE=vector RAG_VECTOR_STORE=qdrant"
  [ -n "${qdrant_url}" ] || fail "RAG status did not include qdrantUrl"
  [ -n "${qdrant_collection}" ] || fail "RAG status did not include qdrantCollection"
  [ "${qdrant_reachable}" = "true" ] || fail "Qdrant is not reachable at ${qdrant_url}; run docker compose up -d qdrant"

  if [ "${embedding_provider}" = "ollama" ]; then
    progress "checking Ollama embedding model ${embedding_model}"
    check_ollama_embedding_model "${embedding_model}"
  fi

  progress 'indexing RAG documents into Qdrant; this can take a while on the first run'
  run_with_dots index curl_post_json "${BACKEND_URL}/api/rag/index" /dev/null "${index_file}"
  documents="$(json_positive_int "${index_file}" documentCount "documentCount")"
  chunks="$(json_positive_int "${index_file}" chunkCount "chunkCount")"
  progress "indexed ${documents} documents into ${chunks} chunks"

  progress "checking Qdrant collection ${qdrant_collection}"
  curl_get_json "${qdrant_url}/collections/${qdrant_collection}" "${qdrant_file}"
  points="$(json_positive_int "${qdrant_file}" result.points_count "qdrant points_count")"
  progress "Qdrant collection has ${points} points"

  progress 'selecting provider and model for the answer generation step'
  curl_get_json "${BACKEND_URL}/api/models" "${models_file}"
  select_provider_and_model "${models_file}" >"${provider_model_file}" \
    || fail "could not select provider/model from /api/models; set RAG_SMOKE_PROVIDER and RAG_SMOKE_MODEL"
  provider="$(sed -n '1p' "${provider_model_file}")"
  model="$(sed -n '2p' "${provider_model_file}")"
  [ -n "${provider}" ] || fail "could not select provider from /api/models; set RAG_SMOKE_PROVIDER"
  [ -n "${model}" ] || fail "could not select model from /api/models; set RAG_SMOKE_MODEL"

  write_query_payload "${query_payload}" "${provider}" "${model}"
  progress "asking RAG question with provider=${provider} model=${model}; Ollama may take 1-3 minutes"
  run_with_dots query curl_post_json "${BACKEND_URL}/api/rag/query" "${query_payload}" "${query_response}"

  answer="$(json_get "${query_response}" answer)"
  source_count="$(json_count "${query_response}" sources)"
  [ -n "${answer}" ] || fail "RAG query returned an empty answer"
  [ "${source_count}" -gt 0 ] || fail "RAG query returned no cited sources"
  progress "RAG answer returned with ${source_count} cited sources"

  printf 'rag qdrant smoke passed\n'
  printf 'backend=%s\n' "${BACKEND_URL}"
  printf 'provider=%s\n' "${provider}"
  printf 'model=%s\n' "${model}"
  printf 'documents=%s chunks=%s qdrant_points=%s sources=%s\n' "${documents}" "${chunks}" "${points}" "${source_count}"
}

main "$@"
