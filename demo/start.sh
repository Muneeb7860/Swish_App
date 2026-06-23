#!/usr/bin/env bash
# Swish closed-beta on the Mac: keep-awake + infra/frontend containers + native backend + tunnel.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
export JWT_SECRET='NiywY/0Zr2Kf/B8mGz9JDo9tFPauu4PKTpgMWALKdJpxFLQgrrRn4iuf3ihfrnqp'
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
[ -f demo/.htpasswd ] || printf "beta:%s\n" "$(openssl passwd -apr1 "${BETA_PASS:-m8FdHvyLDP}")" > demo/.htpasswd

echo "▶ keep-awake (caffeinate)…"; caffeinate -dimsu & echo $! > /tmp/swish_caffeinate.pid
echo "▶ ensure Docker (colima)…"; docker info >/dev/null 2>&1 || colima start --cpu 4 --memory 8
echo "▶ infra + frontend (containers)…"; docker compose -f docker-compose.demo.yml up -d --build
echo "▶ wait for Postgres…"; until docker compose -f docker-compose.demo.yml exec -T postgres pg_isready -U postgres -d swiss_db >/dev/null 2>&1; do sleep 2; done

echo "▶ backend (native, arm64)…"
( cd backend && nohup env \
    SPRING_PROFILES_ACTIVE=staging \
    SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/swiss_db?sslmode=disable" \
    SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=swisssecure2026 DB_SSL_MODE=disable \
    SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
    SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PASSWORD=redissecurepassword \
    SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/swiss_olap" \
    JWT_SECRET="$JWT_SECRET" ADMIN_PASSWORD="swiss-secure-password" \
    mvn -q spring-boot:run > "$ROOT/demo/backend.log" 2>&1 & echo $! > /tmp/swish_backend.pid )

echo "▶ wait for backend health…"; until curl -sf http://localhost:8083/actuator/health >/dev/null 2>&1; do sleep 3; done
echo "✅ Local: http://localhost:3000   (login: beta / m8FdHvyLDP)"

if command -v cloudflared >/dev/null 2>&1; then
  echo "▶ tunnel…"; nohup cloudflared tunnel --url http://localhost:3000 > "$ROOT/demo/tunnel.log" 2>&1 & echo $! > /tmp/swish_tunnel.pid
  sleep 6; grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$ROOT/demo/tunnel.log" | head -1 | sed 's/^/✅ Public URL: /'
else echo "ℹ︎ cloudflared not installed → 'brew install cloudflared' then re-run for a public URL"; fi
