#!/usr/bin/env bash
# Stop and remove the ApollonCASE docker stack (containers + network).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

docker compose -f docker/compose.local.yml down "$@"
