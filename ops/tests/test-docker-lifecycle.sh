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
if [ "${1:-}" = "compose" ] && [ "${2:-}" = "ps" ]; then
  printf '%s\n' 'NAME          STATUS'
  printf '%s\n' 'llm-backend   running'
  printf '%s\n' 'llm-frontend  running'
  printf '%s\n' 'llm-qdrant    running'
fi
EOF

  chmod +x "${bin_dir}/docker"
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

  output="$(run_script "${tmp_dir}" docker-start.sh)"

  assert_contains "${output}" 'Starting local-genai-lab with Docker Compose'
  assert_contains "${output}" 'Docker stack started.'
  assert_contains "${output}" 'frontend: http://localhost:3000'
  assert_contains "${output}" 'backend: http://localhost:8080'
  assert_contains "${output}" 'qdrant: http://localhost:6333'
  assert_file_contains "${tmp_dir}/docker.log" 'compose up -d --build'
  rm -rf "${tmp_dir}"
}

test_docker_stop_runs_compose_down() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"

  output="$(run_script "${tmp_dir}" docker-stop.sh)"

  assert_contains "${output}" 'Stopping local-genai-lab Docker Compose stack'
  assert_contains "${output}" 'Docker stack stopped.'
  assert_file_contains "${tmp_dir}/docker.log" 'compose down'
  rm -rf "${tmp_dir}"
}

test_docker_restart_runs_down_then_up() {
  local tmp_dir output expected_log actual_log
  tmp_dir="$(mktemp -d)"
  mkdir -p "${tmp_dir}/bin"
  : >"${tmp_dir}/docker.log"
  write_mock_docker "${tmp_dir}/bin"

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

  output="$(run_script "${tmp_dir}" docker-status.sh)"

  assert_contains "${output}" 'local-genai-lab Docker Compose status'
  assert_contains "${output}" 'llm-backend'
  assert_contains "${output}" 'expected URLs:'
  assert_contains "${output}" 'frontend: http://localhost:3000'
  assert_file_contains "${tmp_dir}/docker.log" 'compose ps'
  rm -rf "${tmp_dir}"
}

main() {
  test_docker_start_runs_compose_up_build
  test_docker_stop_runs_compose_down
  test_docker_restart_runs_down_then_up
  test_docker_status_runs_compose_ps
  printf 'docker lifecycle tests passed\n'
}

main "$@"
