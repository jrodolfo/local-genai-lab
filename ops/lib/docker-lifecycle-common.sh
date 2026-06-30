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
