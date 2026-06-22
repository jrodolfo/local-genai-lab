#!/usr/bin/env bash
#
# test-docker-lifecycle.sh
#
# Purpose:
#   Unit tests for root Docker lifecycle scripts. Uses a mocked docker command
#   so tests verify script behavior without starting real containers.
#
# Usage:
#   ./ops/tests/test-docker-lifecycle.sh
#
# Required Tools:
#   - bash
#   - mktemp
#
# Expected Output:
#   Success message: "docker lifecycle tests passed"
#   Failure message and non-zero exit code if assertions fail.
#
# Exit Behavior:
#   Exits with 0 on success, 1 on any test failure.
#

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

assert_file_contains() {
  local path="$1"
  local needle="$2"
  local contents

  contents="$(cat "${path}")"
  assert_contains "${contents}" "${needle}"
}

write_mock_docker() {
  local bin_dir="$1"

  cat >"${bin_dir}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${MOCK_DOCKER_LOG}"
if [ "${MOCK_DOCKER_FAIL_UP:-false}" = "true" ] \
  && [ "${1:-}" = "compose" ] \
  && [ "${2:-}" = "up" ]; then
  printf '%s\n' 'mock compose up failed' >&2
  exit 1
fi
if [ "${1:-}" = "compose" ] && [ "${2:-}" = "ps" ]; then
  printf '%s\n' 'NAME          STATUS'
  printf '%s\n' 'llm-backend   running'
  printf '%s\n' 'llm-frontend  running'
  printf '%s\n' 'llm-qdrant    running'
fi
EOF

  chmod +x "${bin_dir}/docker"
}

write_mock_curl() {
  local bin_dir="$1"

  cat >"${bin_dir}/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
url="${*: -1}"
case "${url}" in
  http://localhost:8080/actuator/health)
    [ "${MOCK_BACKEND_HTTP:-ok}" = "ok" ] && exit 0
    ;;
  http://localhost:3000)
    [ "${MOCK_FRONTEND_HTTP:-ok}" = "ok" ] && exit 0
    ;;
  http://localhost:6333)
    [ "${MOCK_QDRANT_HTTP:-ok}" = "ok" ] && exit 0
    ;;
  http://localhost:8080/api/models)
    [ "${MOCK_MODELS_API:-ok}" = "ok" ] && exit 0
    ;;
  http://localhost:8080/api/rag/status)
    [ "${MOCK_RAG_STATUS_API:-ok}" = "ok" ] && exit 0
    ;;
esac
exit 1
EOF

  chmod +x "${bin_dir}/curl"
}

run_script() {
  local tmp_dir="$1"
  local script_name="$2"
  shift 2

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:/usr/bin:/bin" \
    MOCK_DOCKER_LOG="${tmp_dir}/docker.log" \
    "$@" \
    bash "${REPO_ROOT}/${script_name}"
}

test_docker_start_runs_compose_up_build() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-start.sh)"

  assert_contains "${output}" 'Starting local-genai-lab with Docker Compose'
  assert_contains "${output}" 'Docker stack started.'
  assert_contains "${output}" 'URLs:'
  assert_contains "${output}" 'frontend: http://localhost:3000'
  assert_contains "${output}" 'backend: http://localhost:8080'
  assert_contains "${output}" 'qdrant: http://localhost:6333'
  assert_contains "${output}" 'Status:'
  assert_contains "${output}" './docker-status.sh'
  assert_contains "${output}" 'Logs:'
  assert_contains "${output}" 'all services: docker compose logs -f'
  assert_contains "${output}" 'backend: docker compose logs -f backend'
  assert_file_contains "${tmp_dir}/docker.log" 'compose up -d --build'
  rm -rf "${tmp_dir}"
}

test_docker_start_failure_prints_actionable_summary() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  set +e
  output="$(run_script "${tmp_dir}" docker-start.sh MOCK_DOCKER_FAIL_UP=true 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-start.sh to fail when docker compose up fails' >&2
    exit 1
  fi
  assert_contains "${output}" 'Docker startup failed.'
  assert_contains "${output}" 'Common cause: one of the Docker ports is already in use.'
  assert_contains "${output}" 'The host-run ./start.sh workflow uses backend port 8080 and frontend port 5173.'
  assert_contains "${output}" 'backend: lsof -nP -iTCP:8080 -sTCP:LISTEN'
  assert_contains "${output}" 'frontend: lsof -nP -iTCP:3000 -sTCP:LISTEN'
  assert_contains "${output}" 'qdrant: lsof -nP -iTCP:6333 -sTCP:LISTEN'
  assert_contains "${output}" 'Free ports:'
  assert_contains "${output}" 'If the PID belongs to this repo host-run app, run: ./stop.sh --all'
  assert_contains "${output}" 'If needed, stop a specific process with: kill <pid>'
  assert_contains "${output}" 'Last resort only: kill -9 <pid>'
  assert_contains "${output}" 'Retry Docker startup with: ./docker-start.sh'
  assert_contains "${output}" 'backend: docker compose logs -f backend'
  assert_file_contains "${tmp_dir}/docker.log" 'compose up -d --build'
  rm -rf "${tmp_dir}"
}

test_docker_stop_runs_compose_down() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-stop.sh)"

  assert_contains "${output}" 'Stopping local-genai-lab Docker Compose stack'
  assert_contains "${output}" 'Docker stack stopped.'
  assert_contains "${output}" 'Status:'
  assert_contains "${output}" './docker-status.sh'
  assert_file_contains "${tmp_dir}/docker.log" 'compose down'
  rm -rf "${tmp_dir}"
}

test_docker_restart_runs_down_then_up() {
  local tmp_dir output expected_log actual_log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-restart.sh)"
  expected_log=$'compose down\ncompose up -d --build'
  actual_log="$(cat "${tmp_dir}/docker.log")"

  assert_contains "${output}" 'Docker stack stopped.'
  assert_contains "${output}" 'Docker stack started.'
  if [ "${actual_log}" != "${expected_log}" ]; then
    printf 'expected docker calls:\n%s\nactual docker calls:\n%s\n' "${expected_log}" "${actual_log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_docker_status_runs_compose_ps() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-status.sh)"

  assert_contains "${output}" 'local-genai-lab Docker Compose status'
  assert_contains "${output}" 'llm-backend'
  assert_contains "${output}" 'Readiness:'
  assert_contains "${output}" 'backend health: ok'
  assert_contains "${output}" 'frontend http: ok'
  assert_contains "${output}" 'qdrant http: ok'
  assert_contains "${output}" 'URLs:'
  assert_contains "${output}" 'frontend: http://localhost:3000'
  assert_contains "${output}" 'Logs:'
  assert_contains "${output}" 'all services: docker compose logs -f'
  assert_contains "${output}" 'Port checks:'
  assert_contains "${output}" 'backend: lsof -nP -iTCP:8080 -sTCP:LISTEN'
  assert_contains "${output}" 'frontend: lsof -nP -iTCP:3000 -sTCP:LISTEN'
  assert_contains "${output}" 'qdrant: lsof -nP -iTCP:6333 -sTCP:LISTEN'
  assert_contains "${output}" 'Free ports:'
  assert_contains "${output}" './stop.sh --all'
  assert_contains "${output}" 'kill <pid>'
  assert_contains "${output}" './docker-start.sh'
  assert_file_contains "${tmp_dir}/docker.log" 'compose ps'
  rm -rf "${tmp_dir}"
}

test_docker_status_reports_unavailable_services_with_next_actions() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-status.sh MOCK_BACKEND_HTTP=down MOCK_FRONTEND_HTTP=down MOCK_QDRANT_HTTP=down)"

  assert_contains "${output}" 'backend health: unavailable'
  assert_contains "${output}" 'frontend http: unavailable'
  assert_contains "${output}" 'qdrant http: unavailable'
  assert_contains "${output}" 'Next actions:'
  assert_contains "${output}" 'inspect backend logs with: docker compose logs -f backend'
  assert_contains "${output}" 'inspect frontend logs with: docker compose logs -f frontend'
  assert_contains "${output}" 'inspect qdrant logs with: docker compose logs -f qdrant'
  rm -rf "${tmp_dir}"
}

test_docker_check_passes_when_all_endpoints_respond() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-check.sh)"

  assert_contains "${output}" 'Checking local-genai-lab Docker Compose stack'
  assert_contains "${output}" 'pass: backend health'
  assert_contains "${output}" 'pass: frontend http'
  assert_contains "${output}" 'pass: qdrant http'
  assert_contains "${output}" 'pass: backend models api'
  assert_contains "${output}" 'pass: backend rag status api'
  assert_contains "${output}" 'Docker smoke check passed.'
  rm -rf "${tmp_dir}"
}

test_docker_check_fails_with_actionable_output() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  set +e
  output="$(run_script "${tmp_dir}" docker-check.sh MOCK_BACKEND_HTTP=down MOCK_MODELS_API=down 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-check.sh to fail when required endpoints are unavailable' >&2
    exit 1
  fi
  assert_contains "${output}" 'fail: backend health'
  assert_contains "${output}" 'url: http://localhost:8080/actuator/health'
  assert_contains "${output}" 'fail: backend models api'
  assert_contains "${output}" 'url: http://localhost:8080/api/models'
  assert_contains "${output}" 'logs: docker compose logs -f backend'
  assert_contains "${output}" 'Docker smoke check failed.'
  assert_contains "${output}" 'Run ./docker-status.sh for Compose status, readiness, logs, and port diagnostics.'
  rm -rf "${tmp_dir}"
}

main() {
  test_docker_start_runs_compose_up_build
  test_docker_start_failure_prints_actionable_summary
  test_docker_stop_runs_compose_down
  test_docker_restart_runs_down_then_up
  test_docker_status_runs_compose_ps
  test_docker_status_reports_unavailable_services_with_next_actions
  test_docker_check_passes_when_all_endpoints_respond
  test_docker_check_fails_with_actionable_output
  printf 'docker lifecycle tests passed\n'
}

main "$@"
