#!/usr/bin/env bash
#
# test-prepare-release.sh
#
# Purpose:
#   Unit tests for scripts/prepare-release.sh using mocked commands.
#
# Usage:
#   ./ops/tests/test-prepare-release.sh
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

write_mock_git() {
  local bin_dir="$1"

  cat >"${bin_dir}/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'git %s\n' "$*" >> "${MOCK_PREPARE_RELEASE_LOG}"
printf 'mock git %s\n' "$*"
EOF

  chmod +x "${bin_dir}/git"
}

write_mock_make() {
  local bin_dir="$1"

  cat >"${bin_dir}/make" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'make %s\n' "$*" >> "${MOCK_PREPARE_RELEASE_LOG}"
printf 'mock make %s\n' "$*"
EOF

  chmod +x "${bin_dir}/make"
}

setup_prepare_release_fixture() {
  local tmp_dir="$1"

  mkdir -p "${tmp_dir}/bin" "${tmp_dir}/system-bin" "${tmp_dir}/repo/scripts"
  cp "${REPO_ROOT}/scripts/prepare-release.sh" "${tmp_dir}/repo/scripts/prepare-release.sh"
  chmod +x "${tmp_dir}/repo/scripts/prepare-release.sh"
  ln -s "$(command -v bash)" "${tmp_dir}/system-bin/bash"
  ln -s "$(command -v basename)" "${tmp_dir}/system-bin/basename"
  ln -s "$(command -v cat)" "${tmp_dir}/system-bin/cat"
  : >"${tmp_dir}/prepare-release.log"
}

run_prepare_release() {
  local tmp_dir="$1"
  shift

  env -i \
    HOME="${HOME:-}" \
    PATH="${tmp_dir}/bin:${tmp_dir}/system-bin" \
    MOCK_PREPARE_RELEASE_LOG="${tmp_dir}/prepare-release.log" \
    bash "${tmp_dir}/repo/scripts/prepare-release.sh" "$@"
}

test_prepare_release_requires_version() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  setup_prepare_release_fixture "${tmp_dir}"

  set +e
  output="$(run_prepare_release "${tmp_dir}" 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected prepare-release.sh to fail without a version' >&2
    exit 1
  fi
  assert_contains "${output}" 'Usage:'
  assert_contains "${output}" 'prepare-release.sh vX.Y.Z'
  rm -rf "${tmp_dir}"
}

test_prepare_release_rejects_invalid_version() {
  local tmp_dir output status
  tmp_dir="$(mktemp -d)"
  setup_prepare_release_fixture "${tmp_dir}"

  set +e
  output="$(run_prepare_release "${tmp_dir}" 0.2.0 2>&1)"
  status=$?
  set -e

  if [ "${status}" -eq 0 ]; then
    printf '%s\n' 'expected prepare-release.sh to fail with an invalid version' >&2
    exit 1
  fi
  assert_contains "${output}" 'Error: version must look like v0.2.0'
  rm -rf "${tmp_dir}"
}

test_prepare_release_prints_help() {
  local tmp_dir output
  tmp_dir="$(mktemp -d)"
  setup_prepare_release_fixture "${tmp_dir}"

  output="$(run_prepare_release "${tmp_dir}" --help)"

  assert_contains "${output}" 'This script does not create tags or publish GitHub Releases.'
  rm -rf "${tmp_dir}"
}

test_prepare_release_runs_expected_commands() {
  local tmp_dir output log expected_log
  tmp_dir="$(mktemp -d)"
  setup_prepare_release_fixture "${tmp_dir}"
  write_mock_git "${tmp_dir}/bin"
  write_mock_make "${tmp_dir}/bin"

  output="$(run_prepare_release "${tmp_dir}" v0.2.0)"
  log="$(cat "${tmp_dir}/prepare-release.log")"
  expected_log=$'git status\ngit pull\nmake release-check\nmake release-check-docker\ngit diff --check\ngit status'

  assert_contains "${output}" 'Local GenAI Lab release preparation'
  assert_contains "${output}" 'version: v0.2.0'
  assert_contains "${output}" '/tmp/local-genai-lab-release-check-v0.2.0.txt'
  assert_contains "${output}" '/tmp/local-genai-lab-release-check-docker-v0.2.0.txt'
  assert_contains "${output}" 'Release preparation passed.'
  assert_contains "${output}" 'Here are the files you need to check to see if the tests are OK:'
  assert_contains "${output}" 'Then inspect the two /tmp files:'
  assert_contains "${output}" 'vi /tmp/local-genai-lab-release-check-v0.2.0.txt'
  assert_contains "${output}" 'vi /tmp/local-genai-lab-release-check-docker-v0.2.0.txt'
  assert_contains "${output}" 'tag: v0.2.0'
  assert_contains "${output}" 'title: local genai lab v0.2.0'
  if [ "${log}" != "${expected_log}" ]; then
    printf 'expected prepare-release calls:\n%s\nactual prepare-release calls:\n%s\n' "${expected_log}" "${log}" >&2
    exit 1
  fi
  rm -rf "${tmp_dir}"
}

main() {
  test_prepare_release_requires_version
  test_prepare_release_rejects_invalid_version
  test_prepare_release_prints_help
  test_prepare_release_runs_expected_commands
  printf '%s\n' 'prepare release tests passed'
}

main "$@"
