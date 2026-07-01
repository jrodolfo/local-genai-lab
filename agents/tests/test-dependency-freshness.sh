#!/usr/bin/env bash
#
# test-dependency-freshness.sh
#
# Mock-based smoke tests for dependency-freshness.sh.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/dependency-freshness.sh"

assert_file_contains() {
  if ! grep -Fq -- "$2" "$1"; then
    printf 'expected file [%s] to contain [%s]\n' "$1" "$2" >&2
    printf '%s\n' '--- file content ---' >&2
    cat "$1" >&2
    exit 1
  fi
}

make_fake_repo() {
  local repo="$1"

  mkdir -p "$repo/backend" "$repo/frontend" "$repo/mcp"
  printf '<project />\n' >"$repo/backend/pom.xml"
  printf '{"name":"frontend"}\n' >"$repo/frontend/package.json"
  printf '{"name":"mcp"}\n' >"$repo/mcp/package.json"
  cat >"$repo/backend/Dockerfile" <<'DOCKERFILE'
FROM maven:3.9.9-eclipse-temurin-21 AS build
FROM eclipse-temurin:21-jre
DOCKERFILE
  cat >"$repo/frontend/Dockerfile" <<'DOCKERFILE'
FROM node:20-alpine AS build
FROM nginx:latest
DOCKERFILE
  cat >"$repo/docker-compose.yml" <<'COMPOSE'
services:
  db:
    image: mysql:latest
  qdrant:
    image: qdrant/qdrant:v1.18.2
COMPOSE
}

make_fake_bin() {
  local bin_dir="$1"

  mkdir -p "$bin_dir"
  cat >"$bin_dir/mvn" <<'MVN'
#!/usr/bin/env bash
printf 'mock mvn %s\n' "$*"
case "$*" in
  *display-parent-updates*) printf 'spring-boot-starter-parent 3.4.3 -> 3.4.5\n' ;;
  *display-dependency-updates*) printf 'jackson-databind 2.18.2 -> 2.18.8\n' ;;
  *display-property-updates*) printf 'otel.version 1.0.0 -> 1.1.0\n' ;;
  *display-plugin-updates*) printf 'maven-surefire-plugin 3.5.6 -> 3.5.7\n' ;;
esac
MVN
  cat >"$bin_dir/npm" <<'NPM'
#!/usr/bin/env bash
printf 'Package Current Wanted Latest Location\n'
case "$(basename "$PWD")" in
  frontend)
    printf '@vitejs/plugin-react 4.7.0 4.7.0 6.0.3 app\n'
    printf 'jsdom 24.1.3 24.1.3 29.1.1 app\n'
    printf 'react 18.3.1 18.3.1 19.2.7 app\n'
    printf 'react-dom 18.3.1 18.3.1 19.2.7 app\n'
    printf 'vite 6.3.5 6.4.3 7.3.5 app\n'
    ;;
  mcp)
    printf 'typescript 5.9.3 5.9.3 6.0.3 app\n'
    printf 'zod 3.25.76 3.25.76 4.4.3 app\n'
    ;;
esac
exit 1
NPM
  cat >"$bin_dir/curl" <<'CURL'
#!/usr/bin/env bash
printf '{"results":[{"name":"latest"},{"name":"1.2.3"}]}\n'
CURL
  chmod +x "$bin_dir/mvn" "$bin_dir/npm" "$bin_dir/curl"
}

test_dependency_freshness_report() {
  local tmp_dir repo bin_dir output

  tmp_dir="$(mktemp -d)"
  repo="$tmp_dir/repo"
  bin_dir="$tmp_dir/bin"
  output="$tmp_dir/output.txt"

  make_fake_repo "$repo"
  make_fake_bin "$bin_dir"

  PATH="$bin_dir:$PATH" \
    REPO_ROOT="$repo" \
    DEPENDENCY_FRESHNESS_REGISTRY=true \
    "$SCRIPT_PATH" >"$output"

  assert_file_contains "$output" "Dependency freshness report"
  assert_file_contains "$output" "mode: report-only"
  assert_file_contains "$output" "Maven freshness"
  assert_file_contains "$output" "spring-boot-starter-parent 3.4.3 -> 3.4.5"
  assert_file_contains "$output" "npm freshness"
  assert_file_contains "$output" "vite 6.3.5 6.4.3 7.3.5 app"
  assert_file_contains "$output" "typescript 5.9.3 5.9.3 6.0.3 app"
  assert_file_contains "$output" "note: npm reports outdated packages above"
  assert_file_contains "$output" "Docker image freshness"
  assert_file_contains "$output" "nginx:latest"
  assert_file_contains "$output" "mysql:latest"
  assert_file_contains "$output" "external Qdrant image; keep pinned to a stable release tag"
  assert_file_contains "$output" "warning: moving tag detected"
  assert_file_contains "$output" "registry: https://registry.hub.docker.com/v2/repositories/library/nginx/tags?page_size=5"
  assert_file_contains "$output" "Triage summary"
  assert_file_contains "$output" "frontend npm: outdated packages reported"
  assert_file_contains "$output" "mcp npm: outdated packages reported"
  assert_file_contains "$output" "Docker: moving tag detected (nginx:latest)"
  assert_file_contains "$output" "Suggested next branches:"
  assert_file_contains "$output" "frontend-jsdom-readiness - jsdom test-environment modernization"
  assert_file_contains "$output" "frontend-vite-readiness - Vite build-tooling modernization"
  assert_file_contains "$output" "frontend-react-19-readiness - React runtime modernization"
  assert_file_contains "$output" "mcp-typescript-6-readiness - MCP TypeScript modernization"
  assert_file_contains "$output" "mcp-zod-4-readiness - MCP Zod modernization"
  assert_file_contains "$output" "docker-image-freshness-triage - replace moving Docker tags with pinned version tags"
  assert_file_contains "$output" "Dependency freshness report completed."

  rm -rf "$tmp_dir"
}

test_missing_tools_are_skipped() {
  local tmp_dir repo output

  tmp_dir="$(mktemp -d)"
  repo="$tmp_dir/repo"
  output="$tmp_dir/output.txt"
  make_fake_repo "$repo"

  PATH="/usr/bin:/bin" \
    REPO_ROOT="$repo" \
    MVN_BIN="definitely-missing-mvn" \
    NPM_BIN="definitely-missing-npm" \
    "$SCRIPT_PATH" >"$output"

  assert_file_contains "$output" "skip: definitely-missing-mvn not found on PATH"
  assert_file_contains "$output" "skip: definitely-missing-npm not found on PATH"
  assert_file_contains "$output" "registry metadata: disabled"

  rm -rf "$tmp_dir"
}

main() {
  test_dependency_freshness_report
  test_missing_tools_are_skipped
  printf 'dependency freshness tests passed\n'
}

main "$@"
