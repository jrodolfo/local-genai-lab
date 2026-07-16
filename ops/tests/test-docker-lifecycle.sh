#!/usr/bin/env bash
# shellcheck shell=bash disable=SC1003,SC2016
#
# test-docker-lifecycle.sh
#
# Purpose:
#   Unit tests for scripts/ Docker lifecycle commands. Uses a mocked docker
#   command so tests verify script behavior without starting real containers.
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

test_docker_backend_image_includes_mcp_runtime_contract() {
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'context: .'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'dockerfile: backend/Dockerfile'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'OLLAMA_BASE_URL: ${DOCKER_OLLAMA_BASE_URL:-http://host.docker.internal:11434}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'OLLAMA_DEFAULT_MODEL: ${OLLAMA_DEFAULT_MODEL:-llama3:8b}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'OLLAMA_READ_TIMEOUT_SECONDS: ${OLLAMA_READ_TIMEOUT_SECONDS:-300}'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'FROM node:20-bookworm-slim AS mcp-build'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'RUN npm ci'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'RUN npm run build && npm prune --omit=dev'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'apt-get install -y --no-install-recommends awscli jq'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'COPY --from=mcp-build /usr/local/bin/node /usr/local/bin/node'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'COPY --from=mcp-build /app/mcp/dist ./mcp/dist'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'COPY --from=mcp-build /app/mcp/node_modules ./mcp/node_modules'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'COPY docs ./docs'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'COPY agents ./agents'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" 'RUN rm -f /usr/bin/pebble \'
  assert_file_contains "${REPO_ROOT}/backend/Dockerfile" '&& mkdir -p /app/data/sessions /app/agents/reports/audit /app/agents/reports/s3-cloudwatch'
  assert_file_contains "${REPO_ROOT}/.dockerignore" 'backend/target'
  assert_file_contains "${REPO_ROOT}/.dockerignore" 'frontend/node_modules'
  assert_file_contains "${REPO_ROOT}/.dockerignore" 'mcp/node_modules'
  assert_file_contains "${REPO_ROOT}/.dockerignore" 'agents/reports'
  assert_file_contains "${REPO_ROOT}/.dockerignore" '.env.docker-aws-tools'
}

test_docker_aws_tools_override_is_opt_in_and_ignored_locally() {
  assert_file_contains "${REPO_ROOT}/docker-compose.aws-tools.yml" 'LOCAL_GENAI_LAB_AWS_DIR'
  assert_file_contains "${REPO_ROOT}/docker-compose.aws-tools.yml" 'target: /root/.aws'
  assert_file_contains "${REPO_ROOT}/docker-compose.aws-tools.yml" 'read_only: true'
  assert_file_contains "${REPO_ROOT}/.env.docker-aws-tools.example" 'LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true'
  assert_file_contains "${REPO_ROOT}/.gitignore" '.env.docker-aws-tools'
}

test_docker_frontend_proxy_supports_long_llm_requests() {
  assert_file_contains "${REPO_ROOT}/frontend/nginx.conf" 'proxy_connect_timeout 10s;'
  assert_file_contains "${REPO_ROOT}/frontend/nginx.conf" 'proxy_send_timeout 300s;'
  assert_file_contains "${REPO_ROOT}/frontend/nginx.conf" 'proxy_read_timeout 300s;'
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
if [ "${MOCK_DOCKER_FAIL_VERSION:-false}" = "true" ] && [ "${1:-}" = "version" ]; then
  printf '%s\n' 'mock docker version failed' >&2
  exit 1
fi
if [ "${MOCK_DOCKER_FAIL_COMPOSE_VERSION:-false}" = "true" ] \
  && [ "${1:-}" = "compose" ] \
  && [ "${2:-}" = "version" ]; then
  printf '%s\n' 'mock docker compose version failed' >&2
  exit 1
fi
if [ "${MOCK_DOCKER_FAIL_RUN:-false}" = "true" ] \
  && [ "${1:-}" = "run" ]; then
  printf '%s\n' 'mock docker run failed' >&2
  exit 1
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
    if [ -n "${MOCK_BACKEND_HEALTH_FAILS_BEFORE_OK:-}" ]; then
      count_file="${MOCK_CURL_STATE_DIR}/backend-health-count"
      count=0
      if [ -f "${count_file}" ]; then
        count="$(cat "${count_file}")"
      fi
      count="$((count + 1))"
      printf '%s\n' "${count}" >"${count_file}"
      [ "${count}" -le "${MOCK_BACKEND_HEALTH_FAILS_BEFORE_OK}" ] && exit 1
      exit 0
    fi
    [ "${MOCK_BACKEND_HTTP:-ok}" = "ok" ] && exit 0
    ;;
  http://localhost:3000)
    [ "${MOCK_FRONTEND_HTTP:-ok}" = "ok" ] && exit 0
    ;;
  http://localhost:6333/healthz)
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
    MOCK_CURL_STATE_DIR="${tmp_dir}" \
    DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/missing-docker-aws-tools.env" \
    "$@" \
    bash "${REPO_ROOT}/scripts/${script_name}"
}

run_script_with_args() {
  local tmp_dir="$1"
  local script_name="$2"
  shift 2

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:/usr/bin:/bin" \
    MOCK_DOCKER_LOG="${tmp_dir}/docker.log" \
    MOCK_CURL_STATE_DIR="${tmp_dir}" \
    DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/missing-docker-aws-tools.env" \
    bash "${REPO_ROOT}/scripts/${script_name}" "$@"
}

run_tunnel_info_with_qdrant_env() {
  local tmp_dir="$1"
  shift

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:/usr/bin:/bin" \
    MOCK_DOCKER_LOG="${tmp_dir}/docker.log" \
    MOCK_CURL_STATE_DIR="${tmp_dir}" \
    DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/missing-docker-aws-tools.env" \
    DOCKER_TUNNEL_INCLUDE_QDRANT=true \
    bash "${REPO_ROOT}/scripts/docker-tunnel-info.sh" "$@"
}

write_mock_verify_scripts() {
  local tmp_dir="$1"
  local bin_dir="${tmp_dir}/repo"
  local script_name

  mkdir -p "${bin_dir}"
  cp "${REPO_ROOT}/scripts/docker-verify.sh" "${bin_dir}/docker-verify.sh"

  for script_name in stop.sh docker-restart.sh docker-status.sh docker-check.sh; do
    cat >"${bin_dir}/${script_name}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf '%s' '${script_name}' >> "\${MOCK_VERIFY_LOG}"
if [ "\$#" -gt 0 ]; then
  printf ' %s' "\$@" >> "\${MOCK_VERIFY_LOG}"
fi
printf '\n' >> "\${MOCK_VERIFY_LOG}"
printf '%s\n' 'mock ${script_name}'
EOF
    chmod +x "${bin_dir}/${script_name}"
  done

  chmod +x "${bin_dir}/docker-verify.sh"
}

run_verify_script() {
  local tmp_dir="$1"

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:/usr/bin:/bin" \
    MOCK_VERIFY_LOG="${tmp_dir}/verify.log" \
    bash "${tmp_dir}/repo/docker-verify.sh"
}

write_mock_full_check_scripts() {
  local tmp_dir="$1"
  local bin_dir="${tmp_dir}/repo"
  local script_name

  mkdir -p "${bin_dir}"
  cp "${REPO_ROOT}/scripts/docker-full-check.sh" "${bin_dir}/docker-full-check.sh"

  for script_name in docker-verify.sh docker-scan.sh; do
    cat >"${bin_dir}/${script_name}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' '${script_name}' >> "\${MOCK_FULL_CHECK_LOG}"
printf '%s\n' 'mock ${script_name}'
EOF
    chmod +x "${bin_dir}/${script_name}"
  done

  chmod +x "${bin_dir}/docker-full-check.sh"
}

run_full_check_script() {
  local tmp_dir="$1"

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:/usr/bin:/bin" \
    MOCK_FULL_CHECK_LOG="${tmp_dir}/full-check.log" \
    bash "${tmp_dir}/repo/docker-full-check.sh"
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
  assert_contains "${output}" 'docker runtime started'
  assert_contains "${output}" 'aws tools:'
  assert_contains "${output}" 'disabled; copy .env.docker-aws-tools.example to .env.docker-aws-tools to enable AWS-backed Agent tools.'
  assert_contains "${output}" 'urls:'
  assert_contains "${output}" 'frontend:       http://localhost:3000'
  assert_contains "${output}" 'backend health: http://localhost:8080/actuator/health'
  assert_contains "${output}" 'backend api:    http://localhost:8080'
  assert_contains "${output}" 'qdrant:         http://localhost:6333'
  assert_contains "${output}" 'logs:'
  assert_contains "${output}" 'backend:  ./scripts/docker-logs.sh backend'
  assert_contains "${output}" 'frontend: ./scripts/docker-logs.sh frontend'
  assert_contains "${output}" 'qdrant:   ./scripts/docker-logs.sh qdrant'
  assert_contains "${output}" 'all:      ./scripts/docker-logs.sh'
  assert_contains "${output}" 'checks:'
  assert_contains "${output}" './scripts/docker-check.sh'
  assert_contains "${output}" './scripts/docker-status.sh'
  assert_contains "${output}" 'docker desktop:'
  assert_contains "${output}" 'containers > local-genai-lab > llm-backend > logs'
  assert_contains "${output}" 'remote access:'
  assert_contains "${output}" './scripts/docker-tunnel-info.sh my-ec2-1'
  assert_contains "${output}" './scripts/docker-tunnel-info.sh --include-qdrant my-ec2-1'
  assert_file_contains "${tmp_dir}/docker.log" 'compose up -d --build'
  rm -rf "${tmp_dir}"
}

test_docker_start_uses_aws_tools_override_when_enabled() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin" "${tmp_dir}/aws"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"
  cat >"${tmp_dir}/docker-aws-tools.env" <<EOF
LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true
LOCAL_GENAI_LAB_AWS_DIR=${tmp_dir}/aws
AWS_PROFILE=default
AWS_REGION=us-east-2
AWS_DEFAULT_REGION=us-east-2
EOF

  output="$(run_script "${tmp_dir}" docker-start.sh DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/docker-aws-tools.env")"

  assert_contains "${output}" 'aws tools:'
  assert_contains "${output}" "enabled with LOCAL_GENAI_LAB_AWS_DIR=${tmp_dir}/aws"
  assert_file_contains "${tmp_dir}/docker.log" "compose -f ${REPO_ROOT}/docker-compose.yml -f ${REPO_ROOT}/docker-compose.aws-tools.yml up -d --build"
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
  assert_contains "${output}" 'The host-run ./scripts/start.sh workflow uses backend port 8080 and frontend port 5173.'
  assert_contains "${output}" 'backend: lsof -nP -iTCP:8080 -sTCP:LISTEN'
  assert_contains "${output}" 'frontend: lsof -nP -iTCP:3000 -sTCP:LISTEN'
  assert_contains "${output}" 'qdrant: lsof -nP -iTCP:6333 -sTCP:LISTEN'
  assert_contains "${output}" 'free ports:'
  assert_contains "${output}" 'If the PID belongs to this repo host-run app, run: ./scripts/stop.sh --all'
  assert_contains "${output}" 'If needed, stop a specific process with: kill <pid>'
  assert_contains "${output}" 'Last resort only: kill -9 <pid>'
  assert_contains "${output}" 'Retry Docker startup with: ./scripts/docker-start.sh'
  assert_contains "${output}" 'backend:  ./scripts/docker-logs.sh backend'
  assert_contains "${output}" 'docker desktop:'
  assert_file_contains "${tmp_dir}/docker.log" 'compose up -d --build'
  rm -rf "${tmp_dir}"
}

test_docker_sanity_check_passes_without_running_container_by_default() {
  local tmp_dir output actual_log expected_log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-sanity-check.sh)"
  expected_log=$'version\ncompose version'
  actual_log="$(cat "${tmp_dir}/docker.log")"

  assert_contains "${output}" 'Docker sanity check'
  assert_contains "${output}" 'checking: Docker daemon... ok'
  assert_contains "${output}" 'checking: Docker Compose plugin... ok'
  assert_contains "${output}" 'skipped: hello-world container check'
  assert_contains "${output}" 'Docker sanity check passed.'
  if [ "${actual_log}" != "${expected_log}" ]; then
    printf 'expected docker calls:\n%s\nactual docker calls:\n%s\n' "${expected_log}" "${actual_log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_docker_sanity_check_can_run_hello_world() {
  local tmp_dir output actual_log expected_log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-sanity-check.sh DOCKER_SANITY_RUN_HELLO_WORLD=true)"
  expected_log=$'version\ncompose version\nrun --rm hello-world'
  actual_log="$(cat "${tmp_dir}/docker.log")"

  assert_contains "${output}" 'checking: hello-world container... ok'
  assert_contains "${output}" 'Docker sanity check passed.'
  if [ "${actual_log}" != "${expected_log}" ]; then
    printf 'expected docker calls:\n%s\nactual docker calls:\n%s\n' "${expected_log}" "${actual_log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_docker_sanity_check_fails_with_actionable_guidance() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  set +e
  output="$(run_script "${tmp_dir}" docker-sanity-check.sh MOCK_DOCKER_FAIL_VERSION=true 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-sanity-check.sh to fail when docker version fails' >&2
    exit 1
  fi
  assert_contains "${output}" 'checking: Docker daemon... failed'
  assert_contains "${output}" 'Docker sanity check failed at: Docker daemon'
  assert_contains "${output}" 'Start or restart Docker Desktop or Docker Engine.'
  assert_contains "${output}" 'docker context ls'
  assert_contains "${output}" 'docker context use desktop-linux'
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
  assert_contains "${output}" 'Current Docker Compose services:'
  assert_contains "${output}" 'Remaining Docker Compose services after stop:'
  assert_contains "${output}" 'Docker stack stopped. Named volumes such as qdrant_data are preserved.'
  assert_contains "${output}" 'status:'
  assert_contains "${output}" './scripts/docker-status.sh'
  assert_file_contains "${tmp_dir}/docker.log" 'compose ps -a'
  assert_file_contains "${tmp_dir}/docker.log" 'compose down --remove-orphans'
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
  expected_log=$'compose ps -a\ncompose down --remove-orphans\ncompose ps -a\ncompose up -d --build'
  actual_log="$(cat "${tmp_dir}/docker.log")"

  assert_contains "${output}" 'Docker stack stopped. Named volumes such as qdrant_data are preserved.'
  assert_contains "${output}" 'docker runtime started'
  assert_contains "${output}" 'checks:'
  assert_contains "${output}" './scripts/docker-check.sh'
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
  assert_contains "${output}" 'urls:'
  assert_contains "${output}" 'frontend:       http://localhost:3000'
  assert_contains "${output}" 'backend health: http://localhost:8080/actuator/health'
  assert_contains "${output}" 'logs:'
  assert_contains "${output}" 'all:      ./scripts/docker-logs.sh'
  assert_contains "${output}" 'docker desktop:'
  assert_contains "${output}" 'containers > local-genai-lab > llm-qdrant > logs'
  assert_contains "${output}" 'port checks:'
  assert_contains "${output}" 'backend: lsof -nP -iTCP:8080 -sTCP:LISTEN'
  assert_contains "${output}" 'frontend: lsof -nP -iTCP:3000 -sTCP:LISTEN'
  assert_contains "${output}" 'qdrant: lsof -nP -iTCP:6333 -sTCP:LISTEN'
  assert_contains "${output}" 'free ports:'
  assert_contains "${output}" './scripts/stop.sh --all'
  assert_contains "${output}" 'kill <pid>'
  assert_contains "${output}" './scripts/docker-start.sh'
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
  assert_contains "${output}" 'inspect backend logs with: ./scripts/docker-logs.sh backend'
  assert_contains "${output}" 'inspect frontend logs with: ./scripts/docker-logs.sh frontend'
  assert_contains "${output}" 'inspect qdrant logs with: ./scripts/docker-logs.sh qdrant'
  rm -rf "${tmp_dir}"
}

test_docker_logs_follows_all_services_by_default() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-logs.sh)"

  assert_file_contains "${tmp_dir}/docker.log" 'compose logs -f'
  [ -z "${output}" ] || assert_contains "${output}" 'NAME'
  rm -rf "${tmp_dir}"
}

test_docker_logs_follows_named_service() {
  local tmp_dir
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  run_script_with_args "${tmp_dir}" docker-logs.sh backend >/dev/null

  assert_file_contains "${tmp_dir}/docker.log" 'compose logs -f backend'
  rm -rf "${tmp_dir}"
}

test_docker_tunnel_info_prints_default_tunnel() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"

  output="$(run_script "${tmp_dir}" docker-tunnel-info.sh)"

  assert_contains "${output}" 'access from your mac:'
  assert_contains "${output}" 'ssh -N \'
  assert_contains "${output}" '  -L 3000:localhost:3000 \'
  assert_contains "${output}" '  -L 8080:localhost:8080 \'
  assert_contains "${output}" '  my-ec2-1'
  assert_contains "${output}" 'frontend:'
  assert_contains "${output}" 'http://localhost:3000'
  assert_contains "${output}" 'backend:'
  assert_contains "${output}" 'http://localhost:8080'
  assert_contains "${output}" 'health:'
  assert_contains "${output}" 'http://localhost:8080/actuator/health'
  assert_contains "${output}" 'tip:'
  assert_contains "${output}" 'leave the SSH tunnel terminal open while using the application.'
  assert_contains "${output}" "press Ctrl+C to close the tunnel when you're finished."
  rm -rf "${tmp_dir}"
}

test_docker_tunnel_info_accepts_custom_host() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"

  output="$(run_script_with_args "${tmp_dir}" docker-tunnel-info.sh lab-host)"

  assert_contains "${output}" '  lab-host'
  rm -rf "${tmp_dir}"
}

test_docker_tunnel_info_can_include_qdrant() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"

  output="$(run_script_with_args "${tmp_dir}" docker-tunnel-info.sh --include-qdrant lab-host)"

  assert_contains "${output}" '  -L 6333:localhost:6333 \'
  assert_contains "${output}" 'qdrant:'
  assert_contains "${output}" 'http://localhost:6333'
  assert_contains "${output}" '  lab-host'
  rm -rf "${tmp_dir}"
}

test_docker_tunnel_info_prints_help() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"

  output="$(run_script_with_args "${tmp_dir}" docker-tunnel-info.sh --help)"

  assert_contains "${output}" 'Usage:'
  assert_contains "${output}" './scripts/docker-tunnel-info.sh [--include-qdrant|--no-qdrant] [ssh-host]'
  assert_contains "${output}" './scripts/docker-tunnel-info.sh --no-qdrant my-ec2-1'
  assert_contains "${output}" 'DOCKER_TUNNEL_HOST'
  rm -rf "${tmp_dir}"
}

test_docker_tunnel_info_no_qdrant_overrides_environment() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"

  output="$(run_tunnel_info_with_qdrant_env "${tmp_dir}" --no-qdrant lab-host)"

  assert_contains "${output}" '  lab-host'
  if [[ "${output}" == *'6333:localhost:6333'* ]]; then
    printf '%s\n' 'expected --no-qdrant to suppress qdrant tunnel' >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_docker_tunnel_info_reports_unknown_option() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"

  set +e
  output="$(run_script_with_args "${tmp_dir}" docker-tunnel-info.sh --banana 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-tunnel-info.sh to fail for unknown option' >&2
    exit 1
  fi
  assert_contains "${output}" 'Error: unknown option: --banana'
  assert_contains "${output}" 'Usage:'
  rm -rf "${tmp_dir}"
}

test_docker_tunnel_info_reports_duplicate_host() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"

  set +e
  output="$(run_script_with_args "${tmp_dir}" docker-tunnel-info.sh first-host second-host 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-tunnel-info.sh to fail for duplicate host arguments' >&2
    exit 1
  fi
  assert_contains "${output}" 'Error: only one ssh-host may be specified.'
  assert_contains "${output}" 'Usage:'
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
  assert_contains "${output}" 'checking: backend health'
  assert_contains "${output}" 'pass: backend health'
  assert_contains "${output}" 'pass: frontend http'
  assert_contains "${output}" 'pass: qdrant http'
  assert_contains "${output}" 'pass: backend models api'
  assert_contains "${output}" 'pass: backend rag status api'
  assert_contains "${output}" 'Docker smoke check passed.'
  rm -rf "${tmp_dir}"
}

test_docker_check_retries_until_backend_health_is_ready() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-check.sh \
    DOCKER_CHECK_TIMEOUT_SECONDS=5 \
    DOCKER_CHECK_INTERVAL_SECONDS=1 \
    MOCK_BACKEND_HEALTH_FAILS_BEFORE_OK=1)"

  assert_contains "${output}" 'checking: backend health .'
  assert_contains "${output}" 'pass: backend health'
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
  output="$(run_script "${tmp_dir}" docker-check.sh \
    DOCKER_CHECK_TIMEOUT_SECONDS=1 \
    DOCKER_CHECK_INTERVAL_SECONDS=1 \
    MOCK_BACKEND_HTTP=down \
    MOCK_MODELS_API=down \
    2>&1)"
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
  assert_contains "${output}" 'Run ./scripts/docker-status.sh for Compose status, readiness, logs, and port diagnostics.'
  rm -rf "${tmp_dir}"
}

test_docker_verify_runs_full_workflow_in_order() {
  local tmp_dir output expected_log actual_log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/verify.log"
  write_mock_verify_scripts "${tmp_dir}"

  output="$(run_verify_script "${tmp_dir}")"
  expected_log=$'stop.sh --all\ndocker-restart.sh\ndocker-status.sh\ndocker-check.sh'
  actual_log="$(cat "${tmp_dir}/verify.log")"

  assert_contains "${output}" 'Docker verification will:'
  assert_contains "${output}" 'stop host-run backend/frontend processes'
  assert_contains "${output}" 'restart the Docker Compose stack'
  assert_contains "${output}" 'run Docker smoke checks'
  assert_contains "${output}" 'Docker verification completed successfully.'
  if [ "${actual_log}" != "${expected_log}" ]; then
    printf 'expected verify calls:\n%s\nactual verify calls:\n%s\n' "${expected_log}" "${actual_log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_docker_full_check_runs_verify_then_scan() {
  local tmp_dir output expected_log actual_log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/full-check.log"
  write_mock_full_check_scripts "${tmp_dir}"

  output="$(run_full_check_script "${tmp_dir}")"
  expected_log=$'docker-verify.sh\ndocker-scan.sh'
  actual_log="$(cat "${tmp_dir}/full-check.log")"

  assert_contains "${output}" 'Docker full check will:'
  assert_contains "${output}" 'run Docker functional verification'
  assert_contains "${output}" 'run Docker image security scan'
  assert_contains "${output}" 'Docker full check completed successfully.'
  if [ "${actual_log}" != "${expected_log}" ]; then
    printf 'expected full check calls:\n%s\nactual full check calls:\n%s\n' "${expected_log}" "${actual_log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

main() {
  test_docker_backend_image_includes_mcp_runtime_contract
  test_docker_aws_tools_override_is_opt_in_and_ignored_locally
  test_docker_frontend_proxy_supports_long_llm_requests
  test_docker_start_runs_compose_up_build
  test_docker_start_uses_aws_tools_override_when_enabled
  test_docker_start_failure_prints_actionable_summary
  test_docker_sanity_check_passes_without_running_container_by_default
  test_docker_sanity_check_can_run_hello_world
  test_docker_sanity_check_fails_with_actionable_guidance
  test_docker_stop_runs_compose_down
  test_docker_restart_runs_down_then_up
  test_docker_status_runs_compose_ps
  test_docker_status_reports_unavailable_services_with_next_actions
  test_docker_logs_follows_all_services_by_default
  test_docker_logs_follows_named_service
  test_docker_tunnel_info_prints_default_tunnel
  test_docker_tunnel_info_accepts_custom_host
  test_docker_tunnel_info_can_include_qdrant
  test_docker_tunnel_info_prints_help
  test_docker_check_passes_when_all_endpoints_respond
  test_docker_check_retries_until_backend_health_is_ready
  test_docker_check_fails_with_actionable_output
  test_docker_verify_runs_full_workflow_in_order
  test_docker_full_check_runs_verify_then_scan
  printf 'docker lifecycle tests passed\n'
}

main "$@"
