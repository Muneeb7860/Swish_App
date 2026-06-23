#!/usr/bin/env bash
# Install the demo backend as a launchd KeepAlive service (survives crashes + login).
# Staged in ~/swish-demo (OUTSIDE ~/Documents) because macOS TCC denies launchd
# processes access to ~/Documents/~Desktop/~Downloads ("Operation not permitted").
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAGE="$HOME/swish-demo"; LABEL="ch.swissqcommerce.demo-backend"
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"

mkdir -p "$STAGE"
JAR=$(ls "$ROOT"/backend/target/backend-*.jar 2>/dev/null | grep -v original | head -1 || true)
if [ -z "$JAR" ]; then
  echo "building backend jar…"; ( cd "$ROOT/backend" && JAVA_HOME="$(/usr/libexec/java_home -v 17)" mvn -q clean package -DskipTests -Djacoco.skip=true )
  JAR=$(ls "$ROOT"/backend/target/backend-*.jar | grep -v original | head -1)
fi
cp "$JAR" "$STAGE/backend.jar"

cat > "$STAGE/run-backend.sh" <<'SH'
#!/usr/bin/env bash
set -u
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export SPRING_PROFILES_ACTIVE=staging
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/swiss_db?sslmode=disable"
export SPRING_DATASOURCE_USERNAME=postgres SPRING_DATASOURCE_PASSWORD=swisssecure2026 DB_SSL_MODE=disable
export SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PASSWORD=redissecurepassword
export SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/swiss_olap"
export JWT_SECRET="${SWISH_JWT_SECRET:-NiywY/0Zr2Kf/B8mGz9JDo9tFPauu4PKTpgMWALKdJpxFLQgrrRn4iuf3ihfrnqp}"
export ADMIN_PASSWORD="swiss-secure-password" PORT=8083
for i in $(seq 1 60); do /usr/bin/nc -z localhost 5433 2>/dev/null && break; sleep 5; done
exec "$JAVA_HOME/bin/java" -XX:MaxRAMPercentage=70 -jar "$HOME/swish-demo/backend.jar"
SH
chmod +x "$STAGE/run-backend.sh"

cat > "$PLIST" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key><array><string>/bin/bash</string><string>$STAGE/run-backend.sh</string></array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ThrottleInterval</key><integer>15</integer>
  <key>StandardOutPath</key><string>$STAGE/backend.log</string>
  <key>StandardErrorPath</key><string>$STAGE/backend.log</string>
</dict></plist>
PL
launchctl unload "$PLIST" 2>/dev/null || true
launchctl load "$PLIST"
echo "✅ launchd backend installed (KeepAlive). Logs: $STAGE/backend.log"
echo "   Re-run this after rebuilding the jar to refresh ~/swish-demo/backend.jar."
