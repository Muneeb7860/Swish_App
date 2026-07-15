#!/usr/bin/env bash
# start_governance_server.sh — Starts the governance FastAPI server for CI red-team testing.
# Waits until the /health endpoint is responsive before exiting.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PORT="${GOVERNANCE_PORT:-8000}"
MAX_WAIT="${MAX_WAIT_SECONDS:-30}"

echo "▶ Starting Homelab AI Governance server on port $PORT..."

cd "$ROOT_DIR"

# Activate virtualenv if present
if [ -f ".venv/bin/activate" ]; then
    # shellcheck disable=SC1091
    source .venv/bin/activate
fi

# Start the server in the background
SWISH_TRACING_ENABLED=false \
PYTHONPATH="$ROOT_DIR/src" \
    uvicorn governance.server:app \
        --host 0.0.0.0 \
        --port "$PORT" \
        --workers 1 \
        --log-level warning \
        &

SERVER_PID=$!
echo "▶ Server PID: $SERVER_PID"

# Write PID so the caller can kill it later
echo "$SERVER_PID" > "$ROOT_DIR/dev/.server.pid"

# Wait for the health endpoint
echo "▶ Waiting for /health to respond (max ${MAX_WAIT}s)..."
for i in $(seq 1 $MAX_WAIT); do
    if curl -sf "http://localhost:$PORT/health" > /dev/null 2>&1; then
        echo "✅ Governance server is UP (${i}s)"
        exit 0
    fi
    sleep 1
done

echo "❌ Governance server did not start within ${MAX_WAIT} seconds."
kill "$SERVER_PID" 2>/dev/null || true
exit 1
