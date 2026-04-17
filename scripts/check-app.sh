#!/bin/bash
# Performs a lightweight local stack check for daily development use.
#
# This script distinguishes backend reachability from backend readiness, then checks the frontend
# dev server and optionally the Ollama tags endpoint. It is broader than backend actuator health
# because it validates the full local setup, not only the Spring Boot process.

set -u

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:5173}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
CHECK_OLLAMA="${CHECK_OLLAMA:-true}"

failures=0

print_status() {
  local name="$1"
  local status="$2"
  local detail="${3:-}"

  if [ -n "$detail" ]; then
    printf '%s: %s (%s)\n' "$name" "$status" "$detail"
  else
    printf '%s: %s\n' "$name" "$status"
  fi
}

check_backend() {
  local body_file http_code response compact
  body_file="$(mktemp)"
  http_code="$(curl -sS -o "$body_file" -w "%{http_code}" "${BACKEND_URL}/actuator/health" 2>/dev/null || true)"

  if [ -z "$http_code" ] || [ "$http_code" = "000" ]; then
    rm -f "$body_file"
    print_status "backend" "failed" "${BACKEND_URL}/actuator/health"
    failures=$((failures + 1))
    return
  fi

  response="$(cat "$body_file")"
  rm -f "$body_file"
  compact="$(printf '%s' "$response" | tr -d '\n\r\t ')"

  if printf '%s' "$compact" | grep -q '"status":"UP"'; then
    print_status "backend" "ok" "${BACKEND_URL}/actuator/health"
    return
  fi

  if printf '%s' "$compact" | grep -q '"status":"DOWN"'; then
    print_status "backend" "up but not ready" "status=DOWN, http=${http_code}"
    failures=$((failures + 1))
    return
  fi

  if printf '%s' "$compact" | grep -q '"status":"OUT_OF_SERVICE"'; then
    print_status "backend" "up but out of service" "status=OUT_OF_SERVICE, http=${http_code}"
    failures=$((failures + 1))
    return
  fi

  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    print_status "backend" "ok" "${BACKEND_URL}/actuator/health"
  else
    print_status "backend" "failed" "unexpected health response (http=${http_code})"
    failures=$((failures + 1))
  fi
}

check_frontend() {
  if curl -fsS "${FRONTEND_URL}" >/dev/null 2>&1; then
    print_status "frontend" "ok" "${FRONTEND_URL}"
  else
    print_status "frontend" "failed" "${FRONTEND_URL}"
    failures=$((failures + 1))
  fi
}

check_ollama() {
  if [ "$CHECK_OLLAMA" != "true" ]; then
    print_status "ollama" "skipped" "CHECK_OLLAMA=${CHECK_OLLAMA}"
    return
  fi

  if curl -fsS "${OLLAMA_URL}/api/tags" >/dev/null 2>&1; then
    print_status "ollama" "ok" "${OLLAMA_URL}/api/tags"
  else
    print_status "ollama" "failed" "${OLLAMA_URL}/api/tags"
    failures=$((failures + 1))
  fi
}

check_backend
check_frontend
check_ollama

if [ "$failures" -eq 0 ]; then
  print_status "overall" "healthy"
  exit 0
fi

print_status "overall" "failed" "${failures} check(s)"
exit 1
