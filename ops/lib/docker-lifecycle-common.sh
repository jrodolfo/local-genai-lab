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

normalize_aws_credential_source() {
  case "${1:-}" in
    host-files|host_files|local-files|local_files|'')
      printf '%s\n' 'host-files'
      ;;
    instance-profile|instance_profile|ec2-instance-profile|ec2_instance_profile)
      printf '%s\n' 'instance-profile'
      ;;
    *)
      printf 'Error: expected LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE to be host-files or instance-profile, got: %s\n' "$1" >&2
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

  LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE="$(normalize_aws_credential_source "${LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE:-host-files}")"
  export LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE

  if [ "${LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS}" = 'true' ] \
    && [ "${LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE}" = 'host-files' ] \
    && [ -z "${LOCAL_GENAI_LAB_AWS_DIR:-}" ]; then
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

  if [ "${LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE}" = 'instance-profile' ]; then
    return 0
  fi

  if [ ! -d "${LOCAL_GENAI_LAB_AWS_DIR}" ]; then
    printf 'Error: LOCAL_GENAI_LAB_AWS_DIR does not exist: %s\n' "${LOCAL_GENAI_LAB_AWS_DIR}" >&2
    printf '%s\n' 'Set LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE=instance-profile on EC2, create the host AWS directory, or set LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=false.' >&2
    exit 1
  fi
}

docker_aws_tools_enabled() {
  load_docker_aws_tools_env
  [ "${LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS}" = 'true' ]
}

docker_compose() {
  validate_docker_aws_tools_config

  if ! docker_aws_tools_enabled; then
    docker compose "$@"
    return
  fi

  case "${LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE}" in
    instance-profile)
      docker compose \
        -f "${REPO_ROOT}/docker-compose.yml" \
        -f "${REPO_ROOT}/docker-compose.aws-instance-profile.yml" \
        "$@"
      ;;
    host-files)
      docker compose \
        -f "${REPO_ROOT}/docker-compose.yml" \
        -f "${REPO_ROOT}/docker-compose.aws-tools.yml" \
        "$@"
      ;;
  esac
}

print_docker_urls() {
  printf '%s\n' \
    'urls:' \
    '  frontend:       http://localhost:3000' \
    '  backend health: http://localhost:8080/actuator/health' \
    '  backend api:    http://localhost:8080' \
    '  qdrant:         http://localhost:6333'
}

print_docker_log_commands() {
  printf '%s\n' \
    'logs:' \
    '  backend:  ./scripts/docker-logs.sh backend' \
    '  frontend: ./scripts/docker-logs.sh frontend' \
    '  qdrant:   ./scripts/docker-logs.sh qdrant' \
    '  all:      ./scripts/docker-logs.sh'
}

print_docker_desktop_guidance() {
  printf '%s\n' \
    'docker desktop:' \
    '  containers > local-genai-lab > llm-backend > logs' \
    '  containers > local-genai-lab > llm-frontend > logs' \
    '  containers > local-genai-lab > llm-qdrant > logs'
}

print_docker_tunnel_guidance() {
  local ssh_host="${DOCKER_TUNNEL_HOST:-my-ec2-3}"

  printf '%s\n' \
    'remote access:' \
    '  ./scripts/docker-tunnel-info.sh' \
    "  ./scripts/docker-tunnel-info.sh --include-qdrant ${ssh_host}"
}

print_docker_port_checks() {
  printf '%s\n' \
    'port checks:' \
    '  backend: lsof -nP -iTCP:8080 -sTCP:LISTEN' \
    '  frontend: lsof -nP -iTCP:3000 -sTCP:LISTEN' \
    '  qdrant: lsof -nP -iTCP:6333 -sTCP:LISTEN'
}

print_docker_free_ports_guidance() {
  printf '%s\n' \
    'free ports:' \
    '  1. Run the port checks above and note the PID using the blocked port.' \
    '  2. If the PID belongs to this repo host-run app, run: ./scripts/stop.sh --all' \
    '  3. If the PID belongs to another app, stop that app normally.' \
    '  4. If needed, stop a specific process with: kill <pid>' \
    '  5. Last resort only: kill -9 <pid>' \
    '  6. Retry Docker startup with: ./scripts/docker-start.sh'
}

print_docker_status_command() {
  printf '%s\n' \
    'status:' \
    '  ./scripts/docker-status.sh'
}

print_docker_runtime_summary() {
  printf '%s\n' '' 'docker runtime started' ''
  if docker_aws_tools_enabled; then
    if [ "${LOCAL_GENAI_LAB_AWS_CREDENTIAL_SOURCE}" = 'instance-profile' ]; then
      printf '%s\n' \
        'aws tools:' \
        '  enabled with EC2 instance profile credentials'
    else
      printf '%s\n' \
        'aws tools:' \
        "  enabled with LOCAL_GENAI_LAB_AWS_DIR=${LOCAL_GENAI_LAB_AWS_DIR}"
    fi
  else
    printf '%s\n' \
      'aws tools:' \
      '  disabled; copy .env.docker-aws-tools.example to .env.docker-aws-tools to enable AWS-backed Agent tools.'
  fi
  printf '%s\n' ''
  print_docker_urls
  printf '%s\n' ''
  print_docker_log_commands
  printf '%s\n' ''
  printf '%s\n' \
    'checks:' \
    '  ./scripts/docker-check.sh' \
    '  ./scripts/docker-aws-preflight.sh' \
    '  ./scripts/docker-status.sh'
  printf '%s\n' ''
  print_docker_desktop_guidance
  printf '%s\n' ''
  print_docker_tunnel_guidance
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
  print_docker_desktop_guidance
}
