#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"

if grep -RInE '(/Users/[^ )",]+|/home/[^ )",]+|[A-Za-z]:\\Users\\)' README.md docs; then
  echo "Public documentation must not contain local absolute user paths." >&2
  exit 1
fi
