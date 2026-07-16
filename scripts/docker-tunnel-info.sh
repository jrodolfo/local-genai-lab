#!/usr/bin/env bash
#
# docker-tunnel-info.sh
#
# Purpose:
#   Prints SSH tunnel commands for accessing the Docker Compose runtime from a
#   local Mac when the stack is running on a remote host, such as an EC2 instance.
#
# Usage:
#   ./scripts/docker-tunnel-info.sh
#   ./scripts/docker-tunnel-info.sh my-ec2-1
#   ./scripts/docker-tunnel-info.sh --include-qdrant my-ec2-1
#
# Environment:
#   DOCKER_TUNNEL_HOST             Default SSH host. Default: my-ec2-1
#   DOCKER_TUNNEL_INCLUDE_QDRANT   Include the Qdrant port when true.
#
# Exit Behavior:
#   Exits with 0 after printing tunnel guidance, 1 for invalid arguments.
#

set -euo pipefail

usage() {
  cat <<EOF
Usage:
  ./scripts/docker-tunnel-info.sh [--include-qdrant] [ssh-host]

Examples:
  ./scripts/docker-tunnel-info.sh
  ./scripts/docker-tunnel-info.sh my-ec2-1
  ./scripts/docker-tunnel-info.sh --include-qdrant my-ec2-1

Environment:
  DOCKER_TUNNEL_HOST             Default SSH host. Default: my-ec2-1
  DOCKER_TUNNEL_INCLUDE_QDRANT   Include the Qdrant port when true.
EOF
}

normalize_bool() {
  case "${1:-}" in
    true|TRUE|True|1|yes|YES|Yes|y|Y)
      printf '%s\n' 'true'
      ;;
    false|FALSE|False|0|no|NO|No|n|N|'')
      printf '%s\n' 'false'
      ;;
    *)
      printf 'Error: expected boolean true/false, got: %s\n' "$1" >&2
      exit 1
      ;;
  esac
}

ssh_host="${DOCKER_TUNNEL_HOST:-my-ec2-1}"
include_qdrant="$(normalize_bool "${DOCKER_TUNNEL_INCLUDE_QDRANT:-false}")"

while [ "$#" -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --include-qdrant)
      include_qdrant='true'
      ;;
    --no-qdrant)
      include_qdrant='false'
      ;;
    -*)
      usage >&2
      exit 1
      ;;
    *)
      if [ "${ssh_host_source:-default}" = 'argument' ]; then
        usage >&2
        exit 1
      fi
      ssh_host="$1"
      ssh_host_source='argument'
      ;;
  esac
  shift
done

if [ -z "${ssh_host}" ]; then
  printf '%s\n' 'Error: ssh-host must not be empty.' >&2
  exit 1
fi

printf '%s\n' 'access from your mac:' ''
printf '%s\n' "ssh -N \\"
printf '%s\n' "  -L 3000:localhost:3000 \\"
printf '%s\n' "  -L 8080:localhost:8080 \\"
if [ "${include_qdrant}" = 'true' ]; then
  printf '%s\n' "  -L 6333:localhost:6333 \\"
fi
printf '%s\n' "  ${ssh_host}"

cat <<EOF

frontend:
  http://localhost:3000

backend:
  http://localhost:8080

health:
  http://localhost:8080/actuator/health
EOF

if [ "${include_qdrant}" = 'true' ]; then
  cat <<EOF

qdrant:
  http://localhost:6333
EOF
fi

cat <<EOF

tip:
  leave the SSH tunnel terminal open while using the application.
  press Ctrl+C to close the tunnel when you're finished.
EOF
