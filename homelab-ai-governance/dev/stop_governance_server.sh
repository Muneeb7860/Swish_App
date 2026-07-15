#!/usr/bin/env bash
# stop_governance_server.sh — Stops the governance server started by start_governance_server.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/.server.pid"

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill "$PID" 2>/dev/null; then
        echo "✅ Governance server (PID $PID) stopped."
    else
        echo "⚠ Server PID $PID not found — already stopped."
    fi
    rm -f "$PID_FILE"
else
    # Fallback: kill by port
    lsof -ti tcp:8000 | xargs kill -9 2>/dev/null || true
    echo "✅ Killed any process on port 8000."
fi
