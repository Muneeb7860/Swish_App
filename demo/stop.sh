#!/usr/bin/env bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
for p in tunnel backend caffeinate; do [ -f /tmp/swish_$p.pid ] && kill "$(cat /tmp/swish_$p.pid)" 2>/dev/null && rm -f /tmp/swish_$p.pid; done
pkill -f spring-boot:run 2>/dev/null || true
docker compose -f docker-compose.demo.yml down
echo "🛑 demo stopped (Postgres data persists in volume swish-demo_swish_demo_pgdata)"
