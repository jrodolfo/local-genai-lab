#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if find "${ROOT_DIR}" -maxdepth 1 -type f -name '*.sh' | grep -q .; then
  printf '%s\n' 'Root-level shell scripts are not allowed. Put scripts developers run directly under scripts/.' >&2
  find "${ROOT_DIR}" -maxdepth 1 -type f -name '*.sh' -print >&2
  exit 1
fi

printf '%s\n' 'root layout tests passed'
