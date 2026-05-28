#!/usr/bin/env bash
#
# test-start-backend-helper.sh
#
# Purpose:
#   Unit tests for the start-backend-helper.sh script. Verifies environment
#   variable loading, overrides, and validation logic.
#
# Usage:
#   ./ops/tests/test-start-backend-helper.sh
#
# Required Tools:
#   - bash
#   - mktemp
#
# Expected Output:
#   Success message: "start-backend-helper tests passed"
#   Failure message and non-zero exit code if assertions fail.
#
# Exit Behavior:
#   Exits with 0 on success, 1 on any test failure.
#

set -euo pipefail

# --- Path Definitions ---
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/start-backend-helper.sh"

# --- Test Helpers ---

# assert_contains
# Purpose: Verifies that a string contains a specific substring.
# Inputs:
#   $1 - Haystack string to search in.
#   $2 - Needle substring to search for.
# Outputs:
#   Prints error message to stderr on failure.
# Exit Behavior: Exits with 1 on failure.
assert_contains() {
  local haystack="$1"
  local needle="$2"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    printf 'expected output to contain [%s]\nactual output:\n%s\n' "${needle}" "${haystack}" >&2
    exit 1
  fi
}

# --- Test Cases ---

# test_env_file_populates_unset_values
# Purpose: Verifies that variables in .env file are loaded when not set in shell.
test_env_file_populates_unset_values() {
  local tmp_dir env_file output

  tmp_dir="$(mktemp -d)"
  env_file="${tmp_dir}/test.env"
  cat >"${env_file}" <<'EOF'
APP_MODEL_PROVIDER=bedrock
BEDROCK_REGION=us-west-2
BEDROCK_MODEL_ID=us.amazon.nova-pro-v1:0
MCP_ENABLED=false
EOF

  output="$(ENV_FILE="${env_file}" DRY_RUN=true bash "${SCRIPT_PATH}")"

  assert_contains "${output}" "Starting backend with provider=bedrock"
  assert_contains "${output}" "bedrock_region=us-west-2"
  assert_contains "${output}" "bedrock_model=us.amazon.nova-pro-v1:0"
  assert_contains "${output}" "mcp_enabled=false"
  rm -rf "${tmp_dir}"
}

# test_shell_env_overrides_env_file
# Purpose: Verifies that shell environment variables take precedence over .env file.
test_shell_env_overrides_env_file() {
  local tmp_dir env_file output

  tmp_dir="$(mktemp -d)"
  env_file="${tmp_dir}/test.env"
  cat >"${env_file}" <<'EOF'
APP_MODEL_PROVIDER=ollama
BEDROCK_REGION=us-east-1
EOF

  output="$(ENV_FILE="${env_file}" DRY_RUN=true APP_MODEL_PROVIDER=bedrock BEDROCK_REGION=eu-west-1 bash "${SCRIPT_PATH}")"

  assert_contains "${output}" "Starting backend with provider=bedrock"
  assert_contains "${output}" "bedrock_region=eu-west-1"
  rm -rf "${tmp_dir}"
}

# test_invalid_default_provider_fails_clearly
# Purpose: Verifies that an unsupported APP_MODEL_PROVIDER causes a clear error.
test_invalid_default_provider_fails_clearly() {
  local tmp_dir env_file

  tmp_dir="$(mktemp -d)"
  env_file="${tmp_dir}/test.env"
  : >"${env_file}"

  if ENV_FILE="${env_file}" DRY_RUN=true APP_MODEL_PROVIDER=invalid bash "${SCRIPT_PATH}" >/tmp/start-backend-helper-invalid.out 2>/tmp/start-backend-helper-invalid.err; then
    printf 'expected invalid APP_MODEL_PROVIDER to fail\n' >&2
    exit 1
  fi

  assert_contains "$(cat /tmp/start-backend-helper-invalid.err)" "APP_MODEL_PROVIDER must be one of: ollama, bedrock, huggingface."
  rm -f /tmp/start-backend-helper-invalid.out /tmp/start-backend-helper-invalid.err
  rm -rf "${tmp_dir}"
}

# test_huggingface_default_requires_token
# Purpose: Verifies that Hugging Face provider requires an API token.
test_huggingface_default_requires_token() {
  local tmp_dir env_file

  tmp_dir="$(mktemp -d)"
  env_file="${tmp_dir}/test.env"
  : >"${env_file}"

  if ENV_FILE="${env_file}" DRY_RUN=true APP_MODEL_PROVIDER=huggingface HUGGINGFACE_API_TOKEN= bash "${SCRIPT_PATH}" >/tmp/start-backend-helper-hf.out 2>/tmp/start-backend-helper-hf.err; then
    printf 'expected Hugging Face default provider without token to fail\n' >&2
    exit 1
  fi

  assert_contains "$(cat /tmp/start-backend-helper-hf.err)" "HUGGINGFACE_API_TOKEN is required when APP_MODEL_PROVIDER=huggingface."
  rm -f /tmp/start-backend-helper-hf.out /tmp/start-backend-helper-hf.err
  rm -rf "${tmp_dir}"
}

# --- Main ---

# main
# Purpose: Entry point for the test suite.
main() {
  test_env_file_populates_unset_values
  test_shell_env_overrides_env_file
  test_invalid_default_provider_fails_clearly
  test_huggingface_default_requires_token
  printf 'start-backend-helper tests passed\n'
}

main "$@"
