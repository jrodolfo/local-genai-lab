#!/usr/bin/env bash
#
# docker-lifecycle-common.sh
#
# Purpose:
#   Shared output helpers for scripts/ Docker lifecycle commands.
#

ensure_docker_available() {
  local script_name="$1"

  if ! command -v docker >/dev/null 2>&1; then
    printf '%s\n' "Error: docker was not found. Install/start Docker, then retry ./scripts/${script_name}." >&2
    exit 1
  fi
}

load_env_defaults() {
  local env_file="$1"
  local line key value

  [ -f "${env_file}" ] || return 0

  while IFS= read -r line || [ -n "${line}" ]; do
    line="${line%$'\r'}"
    if [[ -z "${line}" || "${line}" == \#* ]]; then
      continue
    fi
    key="${line%%=*}"
    value="${line#*=}"
    if [[ -z "${key}" || "${key}" == "${line}" ]]; then
      continue
    fi
    if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      continue
    fi
    if [ -n "${!key+x}" ]; then
      continue
    fi
    if [[ "${value}" =~ ^\".*\"$ || "${value}" =~ ^\'.*\'$ ]]; then
      value="${value:1:${#value}-2}"
    fi
    export "${key}=${value}"
  done <"${env_file}"
}

normalize_bool() {
  case "${1:-}" in
    true|TRUE|True|1|yes|YES|Yes|y|Y)
      printf '%s\n' 'true'
      ;;
    false|FALSE|False|0|no|NO|No|n|N|'')
      printf '%s\n' 'false'
      ;;
    *)
      printf 'Error: expected boolean true/false, got: %s\n' "$1" >&2
      exit 1
      ;;
  esac
}

load_docker_aws_tools_env() {
  if [ "${DOCKER_AWS_TOOLS_ENV_LOADED:-false}" = 'true' ]; then
    return 0
  fi

  DOCKER_AWS_TOOLS_ENV_FILE="${DOCKER_AWS_TOOLS_ENV_FILE:-${REPO_ROOT}/.env.docker-aws-tools}"
  load_env_defaults "${DOCKER_AWS_TOOLS_ENV_FILE}"
  LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS="$(normalize_bool "${LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS:-false}")"
  export LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS

  if [ "${LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS}" = 'true' ] && [ -z "${LOCAL_GENAI_LAB_AWS_DIR:-}" ]; then
    if [ -z "${HOME:-}" ]; then
      printf '%s\n' 'Error: LOCAL_GENAI_LAB_AWS_DIR is required when HOME is not set.' >&2
      exit 1
    fi
    LOCAL_GENAI_LAB_AWS_DIR="${HOME}/.aws"
    export LOCAL_GENAI_LAB_AWS_DIR
  fi

  DOCKER_AWS_TOOLS_ENV_LOADED='true'
  export DOCKER_AWS_TOOLS_ENV_LOADED
}

validate_docker_aws_tools_config() {
  load_docker_aws_tools_env

  if [ "${LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS}" != 'true' ]; then
    return 0
  fi

  if [ ! -d "${LOCAL_GENAI_LAB_AWS_DIR}" ]; then
    printf 'Error: LOCAL_GENAI_LAB_AWS_DIR does not exist: %s\n' "${LOCAL_GENAI_LAB_AWS_DIR}" >&2
    printf '%s\n' 'Create .env.docker-aws-tools from .env.docker-aws-tools.example, or set LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=false.' >&2
    exit 1
  fi
}

docker_aws_tools_enabled() {
  load_docker_aws_tools_env
  [ "${LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS}" = 'true' ]
}

docker_compose() {
  validate_docker_aws_tools_config

  if docker_aws_tools_enabled; then
    docker compose \
      -f "${REPO_ROOT}/docker-compose.yml" \
      -f "${REPO_ROOT}/docker-compose.aws-tools.yml" \
      "$@"
  else
    docker compose "$@"
  fi
}

print_docker_urls() {
  printf '%s\n' \
    'URLs:' \
    '  frontend: http://localhost:3000' \
    '  backend: http://localhost:8080' \
    '  qdrant: http://localhost:6333'
}

print_docker_log_commands() {
  printf '%s\n' \
    'Logs:' \
    '  all services: docker compose logs -f' \
    '  backend: docker compose logs -f backend' \
    '  frontend: docker compose logs -f frontend' \
    '  qdrant: docker compose logs -f qdrant'
}

print_docker_port_checks() {
  printf '%s\n' \
    'Port checks:' \
    '  backend: lsof -nP -iTCP:8080 -sTCP:LISTEN' \
    '  frontend: lsof -nP -iTCP:3000 -sTCP:LISTEN' \
    '  qdrant: lsof -nP -iTCP:6333 -sTCP:LISTEN'
}

print_docker_free_ports_guidance() {
  printf '%s\n' \
    'Free ports:' \
    '  1. Run the port checks above and note the PID using the blocked port.' \
    '  2. If the PID belongs to this repo host-run app, run: ./scripts/stop.sh --all' \
    '  3. If the PID belongs to another app, stop that app normally.' \
    '  4. If needed, stop a specific process with: kill <pid>' \
    '  5. Last resort only: kill -9 <pid>' \
    '  6. Retry Docker startup with: ./scripts/docker-start.sh'
}

print_docker_status_command() {
  printf '%s\n' \
    'Status:' \
    '  ./scripts/docker-status.sh'
}

print_docker_runtime_summary() {
  if docker_aws_tools_enabled; then
    printf '%s\n' "Docker AWS tools: enabled with LOCAL_GENAI_LAB_AWS_DIR=${LOCAL_GENAI_LAB_AWS_DIR}"
  else
    printf '%s\n' 'Docker AWS tools: disabled; copy .env.docker-aws-tools.example to .env.docker-aws-tools to enable AWS-backed Agent tools.'
  fi
  print_docker_urls
  print_docker_status_command
  print_docker_log_commands
  printf '%s\n' \
    'Next step:' \
    '  ./scripts/docker-check.sh' \
    '  verifies backend, frontend, Qdrant, /api/models, and /api/rag/status'
}

print_docker_start_failure_summary() {
  printf '%s\n' \
    '' \
    'Docker startup failed.' \
    'Common cause: one of the Docker ports is already in use.' \
    'The host-run ./scripts/start.sh workflow uses backend port 8080 and frontend port 5173.' \
    'The full Docker workflow uses backend port 8080, frontend port 3000, and Qdrant port 6333.' \
    ''
  print_docker_status_command
  print_docker_port_checks
  print_docker_free_ports_guidance
  print_docker_log_commands
}
