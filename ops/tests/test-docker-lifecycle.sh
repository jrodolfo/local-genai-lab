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
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'APP_MODEL_PROVIDER: ${APP_MODEL_PROVIDER:-ollama}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'OLLAMA_BASE_URL: ${DOCKER_OLLAMA_BASE_URL:-http://host.docker.internal:11434}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'OLLAMA_DEFAULT_MODEL: ${OLLAMA_DEFAULT_MODEL:-llama3:8b}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'OLLAMA_READ_TIMEOUT_SECONDS: ${OLLAMA_READ_TIMEOUT_SECONDS:-300}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'BEDROCK_REGION: ${BEDROCK_REGION:-us-east-2}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'BEDROCK_MODEL_ID: ${BEDROCK_MODEL_ID:-}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'HUGGINGFACE_API_TOKEN: ${HUGGINGFACE_API_TOKEN:-}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'HUGGINGFACE_DEFAULT_MODEL: ${HUGGINGFACE_DEFAULT_MODEL:-}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'MCP_ENABLED: ${MCP_ENABLED:-true}'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" 'extra_hosts:'
  assert_file_contains "${REPO_ROOT}/docker-compose.yml" '"host.docker.internal:host-gateway"'
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
if [ "${1:-}" = "inspect" ]; then
  if [ "${MOCK_DOCKER_BACKEND_RUNNING:-true}" = "true" ]; then
    printf '%s\n' 'true'
    exit 0
  fi
  printf '%s\n' 'false'
  exit 1
fi
if [ "${1:-}" = "exec" ]; then
  command_text="$*"
  if [[ "${command_text}" == *'test -d /root/.aws'* ]]; then
    [ "${MOCK_DOCKER_AWS_MOUNT:-true}" = "true" ] && exit 0
    exit 1
  fi
  if [[ "${command_text}" == *'command -v aws'* ]]; then
    [ "${MOCK_DOCKER_AWS_TOOLS:-true}" = "true" ] && exit 0
    exit 1
  fi
  if [[ "${command_text}" == *'aws sts get-caller-identity'* ]]; then
    if [ "${MOCK_DOCKER_STS:-true}" = "true" ]; then
      printf '%s\n' 'account: 123456789012'
      printf '%s\n' 'arn: arn:aws:iam::123456789012:role/mock-agent'
      exit 0
    fi
    printf '%s\n' 'mock sts failure' >&2
    exit 1
  fi
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

  for script_name in docker-sanity-check.sh stop.sh docker-restart.sh docker-status.sh docker-check.sh; do
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

write_mock_docker_go_scripts() {
  local tmp_dir="$1"
  local bin_dir="${tmp_dir}/repo"
  local script_name

  mkdir -p "${bin_dir}"
  cp "${REPO_ROOT}/scripts/docker-go.sh" "${bin_dir}/docker-go.sh"

  for script_name in build.sh docker-restart.sh docker-check.sh docker-aws-preflight.sh; do
    cat >"${bin_dir}/${script_name}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' '${script_name}' >> "\${MOCK_DOCKER_GO_LOG}"
if [ "\${MOCK_DOCKER_GO_FAIL_SCRIPT:-}" = '${script_name}' ]; then
  printf '%s\n' 'mock ${script_name} failure' >&2
  exit 1
fi
printf '%s\n' 'mock ${script_name}'
EOF
    chmod +x "${bin_dir}/${script_name}"
  done

  chmod +x "${bin_dir}/docker-go.sh"
}

run_docker_go_script() {
  local tmp_dir="$1"
  shift

  env -i \
    HOME="${HOME:-}" \
    PATH="/usr/bin:/bin" \
    MOCK_DOCKER_GO_LOG="${tmp_dir}/docker-go.log" \
    MOCK_DOCKER_GO_FAIL_SCRIPT="${MOCK_DOCKER_GO_FAIL_SCRIPT:-}" \
    DOCKER_GO_TUNNEL_HOST="${DOCKER_GO_TUNNEL_HOST:-}" \
    bash "${tmp_dir}/repo/docker-go.sh" "$@"
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
  assert_contains "${output}" './scripts/docker-aws-preflight.sh'
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

test_docker_aws_preflight_validates_mounted_identity() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin" "${tmp_dir}/aws"
  : >"${tmp_dir}/docker.log"
  : >"${tmp_dir}/aws/config"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"
  cat >"${tmp_dir}/docker-aws-tools.env" <<EOF
LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true
LOCAL_GENAI_LAB_AWS_DIR=${tmp_dir}/aws
EOF

  output="$(run_script "${tmp_dir}" docker-aws-preflight.sh DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/docker-aws-tools.env")"

  assert_contains "${output}" 'Docker AWS preflight'
  assert_contains "${output}" 'checking: AWS configuration directory on host... pass'
  assert_contains "${output}" 'checking: backend container is running... pass'
  assert_contains "${output}" 'checking: AWS directory is mounted in backend... pass'
  assert_contains "${output}" 'checking: AWS CLI and jq in backend... pass'
  assert_contains "${output}" 'checking: AWS STS identity... pass'
  assert_contains "${output}" 'account: 123456789012'
  assert_contains "${output}" 'Docker AWS preflight passed.'
  assert_file_contains "${tmp_dir}/docker.log" 'inspect --format {{.State.Running}} llm-backend'
  assert_file_contains "${tmp_dir}/docker.log" 'exec llm-backend sh -lc test -d /root/.aws'
  assert_file_contains "${tmp_dir}/docker.log" 'exec llm-backend sh -lc aws sts get-caller-identity'
  rm -rf "${tmp_dir}"
}

test_docker_aws_preflight_requires_enabled_aws_tools() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"

  set +e
  output="$(run_script "${tmp_dir}" docker-aws-preflight.sh 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-aws-preflight.sh to fail when AWS tools are disabled' >&2
    exit 1
  fi
  assert_contains "${output}" 'Docker AWS tools are disabled.'
  assert_contains "${output}" '.env.docker-aws-tools.example'
  rm -rf "${tmp_dir}"
}

test_docker_aws_preflight_requires_host_aws_directory() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"
  cat >"${tmp_dir}/docker-aws-tools.env" <<EOF
LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true
LOCAL_GENAI_LAB_AWS_DIR=${tmp_dir}/missing-aws
EOF

  set +e
  output="$(run_script "${tmp_dir}" docker-aws-preflight.sh DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/docker-aws-tools.env" 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-aws-preflight.sh to fail when the host AWS directory is unavailable' >&2
    exit 1
  fi
  assert_contains "${output}" 'AWS configuration directory on host... fail'
  assert_contains "${output}" 'Verify the path in .env.docker-aws-tools'
  rm -rf "${tmp_dir}"
}

test_docker_aws_preflight_reports_missing_backend_mount() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin" "${tmp_dir}/aws"
  : >"${tmp_dir}/docker.log"
  : >"${tmp_dir}/aws/config"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"
  cat >"${tmp_dir}/docker-aws-tools.env" <<EOF
LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true
LOCAL_GENAI_LAB_AWS_DIR=${tmp_dir}/aws
EOF

  set +e
  output="$(run_script "${tmp_dir}" docker-aws-preflight.sh DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/docker-aws-tools.env" MOCK_DOCKER_AWS_MOUNT=false 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-aws-preflight.sh to fail when the backend AWS mount is unavailable' >&2
    exit 1
  fi
  assert_contains "${output}" 'AWS directory is mounted in backend... fail'
  assert_contains "${output}" 'Verify LOCAL_GENAI_LAB_AWS_DIR and restart Docker'
  rm -rf "${tmp_dir}"
}

test_docker_aws_preflight_reports_missing_backend_utilities() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin" "${tmp_dir}/aws"
  : >"${tmp_dir}/docker.log"
  : >"${tmp_dir}/aws/config"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"
  cat >"${tmp_dir}/docker-aws-tools.env" <<EOF
LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true
LOCAL_GENAI_LAB_AWS_DIR=${tmp_dir}/aws
EOF

  set +e
  output="$(run_script "${tmp_dir}" docker-aws-preflight.sh DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/docker-aws-tools.env" MOCK_DOCKER_AWS_TOOLS=false 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-aws-preflight.sh to fail when the backend utilities are unavailable' >&2
    exit 1
  fi
  assert_contains "${output}" 'AWS CLI and jq in backend... fail'
  assert_contains "${output}" 'Rebuild with ./scripts/build.sh and restart with ./scripts/docker-restart.sh.'
  rm -rf "${tmp_dir}"
}

test_docker_aws_preflight_reports_sts_failure() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin" "${tmp_dir}/aws"
  : >"${tmp_dir}/docker.log"
  : >"${tmp_dir}/aws/config"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_curl "${tmp_dir}/bin"
  cat >"${tmp_dir}/docker-aws-tools.env" <<EOF
LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true
LOCAL_GENAI_LAB_AWS_DIR=${tmp_dir}/aws
EOF

  set +e
  output="$(run_script "${tmp_dir}" docker-aws-preflight.sh DOCKER_AWS_TOOLS_ENV_FILE="${tmp_dir}/docker-aws-tools.env" MOCK_DOCKER_STS=false 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-aws-preflight.sh to fail when STS authentication fails' >&2
    exit 1
  fi
  assert_contains "${output}" 'checking: AWS STS identity... fail'
  assert_contains "${output}" 'Verify AWS_PROFILE, credential validity, region settings, and IAM access.'
  rm -rf "${tmp_dir}"
}

test_docker_go_runs_preparation_in_order() {
  local tmp_dir output expected_log actual_log
  tmp_dir="$(mktemp -d)"
  : >"${tmp_dir}/docker-go.log"
  write_mock_docker_go_scripts "${tmp_dir}"

  output="$(run_docker_go_script "${tmp_dir}")"
  expected_log=$'build.sh\ndocker-restart.sh\ndocker-check.sh\ndocker-aws-preflight.sh'
  actual_log="$(cat "${tmp_dir}/docker-go.log")"

  assert_contains "${output}" 'Docker Agent preparation'
  assert_contains "${output}" '==> 1. build local artifacts'
  assert_contains "${output}" '==> 4. verify Docker AWS identity'
  assert_contains "${output}" 'Docker deployment is ready for AWS Agent testing.'
  assert_contains "${output}" 'For local Docker testing, open: http://localhost:3000'
  assert_contains "${output}" 'set DOCKER_GO_TUNNEL_HOST to print SSH tunnel guidance.'
  assert_contains "${output}" 'Incognito window or DevTools Empty Cache and Hard Reload.'
  if [ "${actual_log}" != "${expected_log}" ]; then
    printf 'expected docker-go calls:\n%s\nactual docker-go calls:\n%s\n' "${expected_log}" "${actual_log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_docker_go_prints_optional_remote_tunnel_guidance() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  : >"${tmp_dir}/docker-go.log"
  write_mock_docker_go_scripts "${tmp_dir}"

  output="$(DOCKER_GO_TUNNEL_HOST=remote-lab run_docker_go_script "${tmp_dir}" --skip-build)"

  assert_contains "${output}" 'ssh -N -L 3001:localhost:3000 remote-lab'
  assert_contains "${output}" 'Then test the remote deployment at: http://localhost:3001'
  assert_contains "${output}" 'separate local Docker deployment may be running.'
  rm -rf "${tmp_dir}"
}

test_docker_go_skip_build_omits_only_build() {
  local tmp_dir output expected_log actual_log
  tmp_dir="$(mktemp -d)"
  : >"${tmp_dir}/docker-go.log"
  write_mock_docker_go_scripts "${tmp_dir}"

  output="$(run_docker_go_script "${tmp_dir}" --skip-build)"
  expected_log=$'docker-restart.sh\ndocker-check.sh\ndocker-aws-preflight.sh'
  actual_log="$(cat "${tmp_dir}/docker-go.log")"

  assert_contains "${output}" 'skipped: build local artifacts (--skip-build)'
  assert_contains "${output}" '==> 1. restart Docker Compose stack'
  if [ "${actual_log}" != "${expected_log}" ]; then
    printf 'expected docker-go calls:\n%s\nactual docker-go calls:\n%s\n' "${expected_log}" "${actual_log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_docker_go_stops_after_failed_step() {
  local tmp_dir output status actual_log
  tmp_dir="$(mktemp -d)"
  : >"${tmp_dir}/docker-go.log"
  write_mock_docker_go_scripts "${tmp_dir}"

  set +e
  output="$(MOCK_DOCKER_GO_FAIL_SCRIPT=docker-check.sh run_docker_go_script "${tmp_dir}" 2>&1)"
  status=$?
  set -e
  actual_log="$(cat "${tmp_dir}/docker-go.log")"

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-go.sh to fail when docker-check.sh fails' >&2
    exit 1
  fi
  assert_contains "${output}" 'fail: smoke-check Docker Compose stack'
  assert_contains "${output}" 'Docker go stopped before the deployment was ready for testing.'
  if [[ "${actual_log}" == *'docker-aws-preflight.sh'* ]]; then
    printf '%s\n' 'expected docker-go.sh not to run AWS preflight after docker-check.sh fails' >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_docker_go_help_and_invalid_options_are_actionable() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  : >"${tmp_dir}/docker-go.log"
  write_mock_docker_go_scripts "${tmp_dir}"

  output="$(run_docker_go_script "${tmp_dir}" --help)"
  assert_contains "${output}" './scripts/docker-go.sh --skip-build'
  assert_contains "${output}" 'DOCKER_GO_TUNNEL_HOST'

  set +e
  output="$(run_docker_go_script "${tmp_dir}" --unknown 2>&1)"
  status=$?
  set -e
  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected docker-go.sh to reject an unsupported option' >&2
    exit 1
  fi
  assert_contains "${output}" 'Error: unsupported option: --unknown'
  rm -rf "${tmp_dir}"
}

test_docker_verify_runs_full_workflow_in_order() {
  local tmp_dir output expected_log actual_log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/verify.log"
  write_mock_verify_scripts "${tmp_dir}"

  output="$(run_verify_script "${tmp_dir}")"
  expected_log=$'docker-sanity-check.sh\nstop.sh --all\ndocker-restart.sh\ndocker-status.sh\ndocker-check.sh'
  actual_log="$(cat "${tmp_dir}/verify.log")"

  assert_contains "${output}" 'Docker verification will:'
  assert_contains "${output}" 'verify Docker daemon and Compose availability'
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
  test_docker_aws_preflight_validates_mounted_identity
  test_docker_aws_preflight_requires_enabled_aws_tools
  test_docker_aws_preflight_requires_host_aws_directory
  test_docker_aws_preflight_reports_missing_backend_mount
  test_docker_aws_preflight_reports_missing_backend_utilities
  test_docker_aws_preflight_reports_sts_failure
  test_docker_go_runs_preparation_in_order
  test_docker_go_prints_optional_remote_tunnel_guidance
  test_docker_go_skip_build_omits_only_build
  test_docker_go_stops_after_failed_step
  test_docker_go_help_and_invalid_options_are_actionable
  test_docker_verify_runs_full_workflow_in_order
  test_docker_full_check_runs_verify_then_scan
  printf 'docker lifecycle tests passed\n'
}

main "$@"
