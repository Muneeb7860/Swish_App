#!/usr/bin/env bash
# ============================================================================
# Swish OS — one-shot UAT / demo test-data seeder
# ============================================================================
# Registers the 5 demo role accounts (via the register API, so passwords are
# app-hashed and the linked oltp.customers rows exist), applies the catalog /
# stores / riders / vendors / purchase-orders + role-promotion SQL, then
# verifies each account can log in with its intended role.
#
# Usage:
#   bash demo/seed-test-data.sh              # against the running demo stack
#   API=http://localhost:8083 PG=swish-demo-postgres-1 bash demo/seed-test-data.sh
#
# Prereqs: demo backend healthy on $API (8083), demo postgres container running.
# ----------------------------------------------------------------------------
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="${API:-http://localhost:8083}"
PG="${PG:-swish-demo-postgres-1}"
DB="${DB:-swiss_db}"
PW="Demo1234!"

GREEN='\033[0;32m'; RED='\033[0;31m'; YEL='\033[1;33m'; NC='\033[0m'
say() { echo -e "$1"; }

# The 5 role accounts (email → intended role, for reporting only; the SQL sets roles)
ACCOUNTS=(customer rider inventory business admin)

say "${YEL}[1/4] Waiting for backend health at ${API}...${NC}"
for i in $(seq 1 30); do
  if curl -sf "$API/actuator/health" >/dev/null 2>&1; then say "${GREEN}✓ backend healthy${NC}"; break; fi
  [ "$i" = 30 ] && { say "${RED}✗ backend not healthy at ${API} — start the demo stack first${NC}"; exit 1; }
  sleep 2
done

say "${YEL}[2/4] Registering role accounts (idempotent; 409/400 = already exists)...${NC}"
for r in "${ACCOUNTS[@]}"; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$API/api/v1/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${r}@swish.local\",\"password\":\"${PW}\"}")
  say "   ${r}@swish.local → HTTP ${code}"
done

say "${YEL}[3/4] Applying test-data.sql (catalog, stores, riders, vendors, POs, role promotion)...${NC}"
if docker exec -i "$PG" psql -U postgres -d "$DB" < "$ROOT/demo/test-data.sql" >/tmp/seed_test_data.out 2>&1; then
  say "${GREEN}✓ SQL applied${NC}"
  grep -E "INSERT|UPDATE" /tmp/seed_test_data.out | tail -8 | sed 's/^/   /'
else
  say "${RED}✗ SQL failed:${NC}"; tail -15 /tmp/seed_test_data.out; exit 1
fi

say "${YEL}[4/4] Verifying logins + roles...${NC}"
verify_login() {
  local email="$1" expect_role="$2"
  local body role
  body=$(curl -s -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"${email}\",\"password\":\"${PW}\"}")
  local tok; tok=$(printf '%s' "$body" | python3 -c "import sys,json;print(json.load(sys.stdin).get('token',''))" 2>/dev/null)
  if [ -z "$tok" ]; then say "${RED}   ✗ ${email}: login failed${NC}"; return; fi
  # role is the middle JWT segment's "role" claim
  role=$(printf '%s' "$tok" | cut -d. -f2 | python3 -c "import sys,base64,json; s=sys.stdin.read().strip(); s+='='*(-len(s)%4); print(json.loads(base64.urlsafe_b64decode(s)).get('role',''))" 2>/dev/null)
  if [ "$role" = "$expect_role" ]; then
    say "${GREEN}   ✓ ${email} → ${role} (token ${#tok} chars)${NC}"
  else
    say "${RED}   ✗ ${email} → got '${role}', expected '${expect_role}'${NC}"
  fi
}
verify_login "customer@swish.local"  "CUSTOMER"
verify_login "rider@swish.local"     "RIDER"
verify_login "business@swish.local"  "WHOLESALER"
verify_login "inventory@swish.local" "WHOLESALER"
verify_login "admin@swish.local"     "ADMIN"

say ""
say "${GREEN}Done.${NC} All demo role accounts use password: ${PW}"
say "  customer@swish.local  (CUSTOMER)   — shop, checkout, orders"
say "  rider@swish.local     (RIDER)      — onboarding, deliveries"
say "  business@swish.local  (WHOLESALER) — B2B restock / procurement"
say "  inventory@swish.local (WHOLESALER) — dark-store inventory"
say "  admin@swish.local     (ADMIN)      — HITL / governance / observability"
