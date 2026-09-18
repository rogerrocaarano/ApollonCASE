#!/usr/bin/env bash
# Build and start the full ApollonCASE stack (webapp + server + redis) in Docker.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "Starting ApollonCASE (docker) — webapp: http://localhost:8080  server: http://localhost:8000 (ws :4444)"
echo "Press Ctrl+C to stop. Run scripts/docker-down.sh afterwards to remove containers/network."

docker compose -f docker/compose.local.yml up --build "$@"
