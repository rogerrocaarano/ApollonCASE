#!/usr/bin/env bash
# Local dev: Redis in Docker (kept running across sessions) + backend/webapp
# on the host with hot-reload. Ctrl+C stops backend+webapp only.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "Ensuring dev Redis is up..."
docker compose -f docker/compose.local.db.yml up -d

echo -n "Waiting for Redis to be ready"
until docker compose -f docker/compose.local.db.yml exec -T db redis-cli ping >/dev/null 2>&1; do
  echo -n "."
  sleep 1
done
echo " ready."

if [ ! -d node_modules ]; then
  echo "Installing dependencies (first run)..."
  npm install
fi

echo "Starting backend (server) and webapp..."
echo "  webapp:  http://localhost:5173"
echo "  server:  http://localhost:8000  (ws :4444)"
echo "Press Ctrl+C to stop backend + webapp."
echo "Redis dev container keeps running — stop it with 'docker compose -f docker/compose.local.db.yml down' when you're done for the day."
echo

npm run dev --workspace=@tumaet/server &
BACKEND_PID=$!

CLEANED_UP=0
cleanup() {
  if [ "$CLEANED_UP" -eq 1 ]; then
    return
  fi
  CLEANED_UP=1
  echo
  echo "Stopping backend..."
  kill "$BACKEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

npm run start --workspace=@tumaet/webapp
