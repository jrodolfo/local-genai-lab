#!/usr/bin/env bash
#
# test-release-check.sh
#
# Purpose:
#   Unit tests for scripts/release-check.sh using mocked commands.
#
# Usage:
#   ./ops/tests/test-release-check.sh
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

write_mock_make() {
  local bin_dir="$1"

  cat >"${bin_dir}/make" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'make %s\n' "$*" >> "${MOCK_RELEASE_CHECK_LOG}"
printf 'mock make %s\n' "$*"
EOF

  chmod +x "${bin_dir}/make"
}

write_mock_git() {
  local bin_dir="$1"

  cat >"${bin_dir}/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'git %s\n' "$*" >> "${MOCK_RELEASE_CHECK_LOG}"
printf 'mock git %s\n' "$*"
EOF

  chmod +x "${bin_dir}/git"
}

write_mock_docker() {
  local bin_dir="$1"

  cat >"${bin_dir}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'docker %s\n' "$*" >> "${MOCK_RELEASE_CHECK_LOG}"
if [ "${MOCK_DOCKER_DAEMON_DOWN:-false}" = "true" ] && [ "${1:-}" = "version" ]; then
  printf '%s\n' 'mock docker daemon unavailable' >&2
  exit 1
fi
if [ "${MOCK_DOCKER_COMPOSE_MISSING:-false}" = "true" ] && [ "${1:-}" = "compose" ]; then
  printf '%s\n' 'mock docker compose unavailable' >&2
  exit 1
fi
printf 'mock docker %s\n' "$*"
EOF

  chmod +x "${bin_dir}/docker"
}

write_mock_trivy() {
  local bin_dir="$1"

  cat >"${bin_dir}/trivy" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'trivy %s\n' "$*" >> "${MOCK_RELEASE_CHECK_LOG}"
printf 'mock trivy %s\n' "$*"
EOF

  chmod +x "${bin_dir}/trivy"
}

setup_release_check_fixture() {
  local tmp_dir="$1"

  mkdir -p "${tmp_dir}/bin" "${tmp_dir}/system-bin" "${tmp_dir}/repo/scripts"
  cp "${REPO_ROOT}/scripts/release-check.sh" "${tmp_dir}/repo/scripts/release-check.sh"
  chmod +x "${tmp_dir}/repo/scripts/release-check.sh"
  ln -s "$(command -v bash)" "${tmp_dir}/system-bin/bash"
  ln -s "$(command -v dirname)" "${tmp_dir}/system-bin/dirname"
  ln -s "$(command -v pwd)" "${tmp_dir}/system-bin/pwd"
  : >"${tmp_dir}/release-check.log"
}

run_release_check() {
  local tmp_dir="$1"
  shift

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:${tmp_dir}/system-bin" \
    MOCK_RELEASE_CHECK_LOG="${tmp_dir}/release-check.log" \
    "$@" \
    bash "${tmp_dir}/repo/scripts/release-check.sh"
}

test_release_check_skips_docker_by_default() {
  local tmp_dir output log
  tmp_dir="$(mktemp -d)"
  setup_release_check_fixture "${tmp_dir}"
  write_mock_make "${tmp_dir}/bin"
  write_mock_git "${tmp_dir}/bin"

  output="$(run_release_check "${tmp_dir}")"
  log="$(cat "${tmp_dir}/release-check.log")"

  assert_contains "${output}" 'Local GenAI Lab release check'
  assert_contains "${output}" 'docker full check: false'
  assert_contains "${output}" 'skipped: set RELEASE_CHECK_DOCKER=true to run make docker-full-check'
  assert_contains "${output}" 'release check passed'
  assert_contains "${log}" 'make test'
  assert_contains "${log}" 'make verify'
  assert_contains "${log}" 'make dependency-freshness'
  assert_contains "${log}" 'git diff --check'
  if grep -Fq 'docker-full-check' "${tmp_dir}/release-check.log"; then
    printf '%s\n' 'expected Docker full check to be skipped by default' >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_release_check_runs_docker_when_requested() {
  local tmp_dir output expected_log actual_log
  tmp_dir="$(mktemp -d)"
  setup_release_check_fixture "${tmp_dir}"
  write_mock_make "${tmp_dir}/bin"
  write_mock_git "${tmp_dir}/bin"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_trivy "${tmp_dir}/bin"

  output="$(run_release_check "${tmp_dir}" RELEASE_CHECK_DOCKER=true)"
  expected_log=$'docker version\ndocker compose version\ntrivy --version\nmake test\nmake verify\nmake dependency-freshness\ngit diff --check\nmake docker-full-check'
  actual_log="$(cat "${tmp_dir}/release-check.log")"

  assert_contains "${output}" 'docker full check: true'
  assert_contains "${output}" 'Docker preflight'
  assert_contains "${output}" 'preflight: Docker daemon... ok'
  assert_contains "${output}" 'preflight: Docker Compose plugin... ok'
  assert_contains "${output}" 'preflight: Trivy... ok'
  assert_contains "${output}" 'Docker full check'
  if [ "${actual_log}" != "${expected_log}" ]; then
    printf 'expected release-check calls:\n%s\nactual release-check calls:\n%s\n' "${expected_log}" "${actual_log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_release_check_fails_when_docker_requested_but_missing() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  setup_release_check_fixture "${tmp_dir}"
  write_mock_make "${tmp_dir}/bin"
  write_mock_git "${tmp_dir}/bin"

  set +e
  output="$(run_release_check "${tmp_dir}" RELEASE_CHECK_DOCKER=true 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected release-check.sh to fail when Docker is requested but unavailable' >&2
    exit 1
  fi
  assert_contains "${output}" 'Error: required command not found: docker'
  rm -rf "${tmp_dir}"
}

test_release_check_fails_fast_when_docker_daemon_is_unavailable() {
  local tmp_dir output status log
  tmp_dir="$(mktemp -d)"
  setup_release_check_fixture "${tmp_dir}"
  write_mock_make "${tmp_dir}/bin"
  write_mock_git "${tmp_dir}/bin"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_trivy "${tmp_dir}/bin"

  set +e
  output="$(run_release_check "${tmp_dir}" RELEASE_CHECK_DOCKER=true MOCK_DOCKER_DAEMON_DOWN=true 2>&1)"
  status=$?
  set -e
  log="$(cat "${tmp_dir}/release-check.log")"

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected release-check.sh to fail when Docker daemon is unavailable' >&2
    exit 1
  fi
  assert_contains "${output}" 'preflight: Docker daemon... failed'
  assert_contains "${output}" 'Docker-inclusive release check requested, but preflight failed: docker version'
  assert_contains "${output}" 'Start or restart Docker Desktop'
  if grep -Fq 'make test' "${tmp_dir}/release-check.log"; then
    printf 'expected release-check to fail before running make targets\nactual log:\n%s\n' "${log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

test_release_check_fails_fast_when_docker_compose_is_unavailable() {
  local tmp_dir output status log
  tmp_dir="$(mktemp -d)"
  setup_release_check_fixture "${tmp_dir}"
  write_mock_make "${tmp_dir}/bin"
  write_mock_git "${tmp_dir}/bin"
  write_mock_docker "${tmp_dir}/bin"
  write_mock_trivy "${tmp_dir}/bin"

  set +e
  output="$(run_release_check "${tmp_dir}" RELEASE_CHECK_DOCKER=true MOCK_DOCKER_COMPOSE_MISSING=true 2>&1)"
  status=$?
  set -e
  log="$(cat "${tmp_dir}/release-check.log")"

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected release-check.sh to fail when Docker Compose is unavailable' >&2
    exit 1
  fi
  assert_contains "${output}" 'preflight: Docker daemon... ok'
  assert_contains "${output}" 'preflight: Docker Compose plugin... failed'
  assert_contains "${output}" 'Docker-inclusive release check requested, but preflight failed: docker compose version'
  if grep -Fq 'make test' "${tmp_dir}/release-check.log"; then
    printf 'expected release-check to fail before running make targets\nactual log:\n%s\n' "${log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

main() {
  test_release_check_skips_docker_by_default
  test_release_check_runs_docker_when_requested
  test_release_check_fails_when_docker_requested_but_missing
  test_release_check_fails_fast_when_docker_daemon_is_unavailable
  test_release_check_fails_fast_when_docker_compose_is_unavailable
  printf '%s\n' 'release check tests passed'
}

main "$@"
