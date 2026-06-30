#!/usr/bin/env bash
#
# dependency-freshness.sh
#
# Report dependency freshness signals for Maven, npm, and Docker references.
# This script is advisory: it does not modify dependency files.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
MVN_BIN="${MVN_BIN:-mvn}"
NPM_BIN="${NPM_BIN:-npm}"
CURL_BIN="${CURL_BIN:-curl}"
DEPENDENCY_FRESHNESS_REGISTRY="${DEPENDENCY_FRESHNESS_REGISTRY:-false}"

section() {
  printf '\n%s\n' "$1"
  printf '%s\n' "--------------------------------------------------------------------------------"
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

run_report_command() {
  local label="$1"
  shift

  printf '\n[%s]\n' "$label"
  set +e
  "$@"
  local status=$?
  set -e
  if [ "$status" -ne 0 ]; then
    printf 'warning: command exited with status %s; continuing because this report is advisory\n' "$status"
  fi
  return 0
}

report_maven() {
  section "Maven freshness"

  local backend_dir="${REPO_ROOT}/backend"
  if [ ! -f "${backend_dir}/pom.xml" ]; then
    printf 'skip: backend/pom.xml not found\n'
    return 0
  fi
  if ! command_exists "$MVN_BIN"; then
    printf 'skip: %s not found on PATH\n' "$MVN_BIN"
    return 0
  fi

  (
    cd "$backend_dir"
    run_report_command "parent updates" "$MVN_BIN" -q versions:display-parent-updates
    run_report_command "dependency updates" "$MVN_BIN" -q versions:display-dependency-updates
    run_report_command "property updates" "$MVN_BIN" -q versions:display-property-updates
    run_report_command "plugin updates" "$MVN_BIN" -q versions:display-plugin-updates
  )
}

report_npm_project() {
  local project_name="$1"
  local project_dir="$2"

  printf '\n[%s npm outdated]\n' "$project_name"
  if [ ! -f "${project_dir}/package.json" ]; then
    printf 'skip: %s/package.json not found\n' "$project_name"
    return 0
  fi
  if ! command_exists "$NPM_BIN"; then
    printf 'skip: %s not found on PATH\n' "$NPM_BIN"
    return 0
  fi

  (
    cd "$project_dir"
    local output_file npm_cache status
    output_file="$(mktemp)"
    npm_cache="${DEPENDENCY_FRESHNESS_NPM_CACHE:-${TMPDIR:-/tmp}/local-genai-lab-npm-cache}"
    mkdir -p "$npm_cache/_logs"
    set +e
    NPM_CONFIG_CACHE="$npm_cache" NPM_CONFIG_LOGS_DIR="$npm_cache/_logs" "$NPM_BIN" outdated >"$output_file" 2>&1
    status=$?
    set -e
    cat "$output_file"
    case "$status" in
      0)
        printf 'ok: no outdated packages reported\n'
        ;;
      1)
        if grep -Eiq '(^npm error|^npm ERR!|ENOTFOUND|EAI_AGAIN|network request|network connectivity)' "$output_file" 2>/dev/null; then
          printf 'warning: npm outdated could not complete; continuing because this report is advisory\n'
        else
          printf 'note: npm reports outdated packages above\n'
        fi
        ;;
      *)
        printf 'warning: npm outdated exited with status %s; continuing because this report is advisory\n' "$status"
        ;;
    esac
    rm -f "$output_file"
  )
}

report_npm() {
  section "npm freshness"
  report_npm_project "frontend" "${REPO_ROOT}/frontend"
  report_npm_project "mcp" "${REPO_ROOT}/mcp"
}

docker_files() {
  find "$REPO_ROOT" \
    -path "${REPO_ROOT}/.git" -prune -o \
    -path "${REPO_ROOT}/data" -prune -o \
    \( -name 'Dockerfile' -o -name 'Dockerfile.*' -o -name 'docker-compose.yml' -o -name 'docker-compose*.yaml' \) \
    -type f -print | sort
}

docker_refs_from_file() {
  local file="$1"
  awk '
    /^[[:space:]]*FROM[[:space:]]+/ {
      image=$2
      if (image != "scratch") {
        print FILENAME ":" FNR ": " image
      }
    }
    /^[[:space:]]*image:[[:space:]]*/ {
      image=$0
      sub(/^[[:space:]]*image:[[:space:]]*/, "", image)
      gsub(/["'\''"]/, "", image)
      if (image != "") {
        print FILENAME ":" FNR ": " image
      }
    }
  ' "$file"
}

image_name() {
  local ref="$1"
  ref="${ref%@*}"
  ref="${ref%%:*}"
  printf '%s\n' "$ref"
}

image_tag() {
  local ref="$1"
  ref="${ref%@*}"
  local slash_part="${ref##*/}"
  if [[ "$slash_part" == *:* ]]; then
    printf '%s\n' "${slash_part##*:}"
  else
    printf 'latest\n'
  fi
}

is_moving_tag() {
  local tag="$1"
  case "$tag" in
    latest|main|master|stable|edge|current)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

docker_hub_tags_url() {
  local image="$1"
  case "$image" in
    */*/*)
      return 1
      ;;
    */*)
      printf 'https://registry.hub.docker.com/v2/repositories/%s/tags?page_size=5\n' "$image"
      ;;
    *)
      printf 'https://registry.hub.docker.com/v2/repositories/library/%s/tags?page_size=5\n' "$image"
      ;;
  esac
}

report_registry_metadata() {
  local image="$1"
  local url

  if [ "$DEPENDENCY_FRESHNESS_REGISTRY" != "true" ]; then
    return 0
  fi
  if ! command_exists "$CURL_BIN"; then
    printf '  registry: skip, %s not found\n' "$CURL_BIN"
    return 0
  fi
  if ! url="$(docker_hub_tags_url "$image")"; then
    printf '  registry: skip, unsupported non-Docker-Hub image name\n'
    return 0
  fi

  printf '  registry: %s\n' "$url"
  if ! "$CURL_BIN" -fsS "$url" | sed -n 's/.*"name":"\([^"]*\)".*/    tag: \1/p' | head -5; then
    printf '  registry: warning, lookup failed\n'
  fi
}

report_docker() {
  section "Docker image freshness"

  local found=false
  while IFS= read -r file; do
    while IFS= read -r line; do
      found=true
      local ref image tag
      ref="${line##*: }"
      image="$(image_name "$ref")"
      tag="$(image_tag "$ref")"
      printf '%s\n' "$line"
      printf '  image: %s\n' "$image"
      printf '  tag: %s\n' "$tag"
      if is_moving_tag "$tag"; then
        printf '  warning: moving tag detected; prefer a pinned version tag for reproducibility\n'
      fi
      report_registry_metadata "$image"
    done < <(docker_refs_from_file "$file")
  done < <(docker_files)

  if [ "$found" != "true" ]; then
    printf 'skip: no Docker image references found\n'
  fi
  if [ "$DEPENDENCY_FRESHNESS_REGISTRY" != "true" ]; then
    printf '\nregistry metadata: disabled; set DEPENDENCY_FRESHNESS_REGISTRY=true to query Docker Hub tag metadata\n'
  fi
}

main() {
  printf '%s\n' "Dependency freshness report"
  printf 'repo: %s\n' "$REPO_ROOT"
  printf '%s\n' "mode: report-only"
  printf '%s\n' "note: this script does not modify dependency files"

  report_maven
  report_npm
  report_docker

  printf '\n%s\n' "Dependency freshness report completed."
}

main "$@"
