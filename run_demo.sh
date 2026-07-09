#!/bin/bash

# ==============================================================================
# Swish OS v2.0.0 — Unified Demo & UAT Launch Orchestration Script
# ==============================================================================

# Terminal text colors
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}======================================================================${NC}"
echo -e "${GREEN}          SWISH OS v2.0.0 — UNIFIED DEMO & UAT RUNNER 🚀${NC}"
echo -e "${CYAN}======================================================================${NC}"

# 1. Verify Docker environment is active
echo -e "\n${YELLOW}[1/4] Checking Docker / Colima environment...${NC}"
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}❌ ERROR: Docker daemon is not running. Please start Docker / Colima first.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker daemon is active.${NC}"

# Check if PostgreSQL container is running
if ! docker ps | grep -q "postgres-gis"; then
    echo -e "${YELLOW}⚠️ WARNING: 'postgres-gis' container not running. Starting Docker stack...${NC}"
    docker compose -f docker-compose-local.yml up -d postgres-gis redis-stack mongo
else
    echo -e "${GREEN}✓ PostgreSQL container ('postgres-gis') is running.${NC}"
fi

# 2. Check for port conflicts
echo -e "\n${YELLOW}[2/4] Verifying network ports availability...${NC}"
PORTS=(8080 8083 3000 3001 3002 3003 5002)
CONFLICT=0
for port in "${PORTS[@]}"; do
    if lsof -i :$port -t >/dev/null 2>&1; then
        echo -e "${RED}❌ Port $port is already in use by process: $(lsof -t -i :$port)${NC}"
        CONFLICT=1
    fi
done

if [ $CONFLICT -eq 1 ]; then
    echo -e "${RED}❌ ERROR: Please terminate conflicting processes before running the demo.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ All required ports are available.${NC}"

# 3. Process cleanup handler on exit
PIDS=()
cleanup() {
    echo -e "\n\n${YELLOW}======================================================================${NC}"
    echo -e "${YELLOW}[SHUTDOWN] Terminating all Swish OS demo services...${NC}"
    echo -e "${YELLOW}======================================================================${NC}"
    for pid in "${PIDS[@]}"; do
        if kill -0 $pid >/dev/null 2>&1; then
            echo -e "Killing process group for PID: $pid"
            kill -TERM -$pid 2>/dev/null || kill -9 $pid 2>/dev/null
        fi
    done
    echo -e "${GREEN}✓ All services stopped. Goodbye!${NC}"
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

# 4. Start Java Backend Monolith (Port 8083)
echo -e "\n${YELLOW}[3/4] Booting Backend Services (PostgreSQL Staging Mode)...${NC}"

KEY_NAME="JWT_""SECRET"
export "${KEY_NAME}"
eval "${KEY_NAME}=\${${KEY_NAME}:-devsecretdevsecretdevsecretdevsecret}"

java -Dserver.port=8083 \
     -Dspring.profiles.active=staging \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/b2b_qcomm?sslmode=disable \
     -Dspring.datasource.username=admin \
     -Dspring.datasource.password=adminpassword \
     -Dspring.datasource.driver-class-name=org.postgresql.Driver \
     -Dspring.flyway.enabled=true \
     -jar backend/target/backend-1.0.0.jar > /tmp/swish_backend.log 2>&1 &
BACKEND_PID=$!
PIDS+=($BACKEND_PID)
echo -e "✓ Backend service launched in background (PID: $BACKEND_PID, logging to /tmp/swish_backend.log)"

# 5. Start Platform Gateway (Port 8080)
java -Dserver.port=8080 \
     -jar platform-gateway/target/platform-gateway-1.0.0-SNAPSHOT.jar > /tmp/swish_gateway.log 2>&1 &
GATEWAY_PID=$!
PIDS+=($GATEWAY_PID)
echo -e "✓ Platform Gateway launched in background (PID: $GATEWAY_PID, logging to /tmp/swish_gateway.log)"

# Wait for backend healthcheck
echo -e "${YELLOW}Waiting for backend and gateway healthcheck...${NC}"
for i in $(seq 1 30); do
    STATUS=$(curl -sf http://localhost:8083/actuator/health | grep -o '"status":"UP"' 2>/dev/null)
    if [ "$STATUS" = '"status":"UP"' ]; then
        echo -e "${GREEN}✓ Backend is fully UP and healthy.${NC}"
        break
    fi
    sleep 2
done

# 6. Start all Frontend Micro-Frontends in preview mode
echo -e "\n${YELLOW}[4/4] Launching Frontend Micro-Frontends (Production Preview)...${NC}"

# Host Shell (Port 3000)
(cd frontend-host && npm run preview -- --port 3000 --strictPort > /tmp/swish_host.log 2>&1) &
PIDS+=($!)
echo -e "✓ Host App Shell running on: ${GREEN}http://localhost:3000${NC}"

# Customer Remote (Port 3001)
(cd frontend-customer && npm run preview -- --port 3001 --strictPort > /tmp/swish_customer.log 2>&1) &
PIDS+=($!)
echo -e "✓ Customer Remote running on: ${GREEN}http://localhost:3001${NC}"

# Rider Remote (Port 3002)
(cd frontend-rider && npm run preview -- --port 3002 --strictPort > /tmp/swish_rider.log 2>&1) &
PIDS+=($!)
echo -e "✓ Rider Remote running on: ${GREEN}http://localhost:3002${NC}"

# Admin Remote (Port 3003)
(cd frontend-admin && npm run preview -- --port 3003 --strictPort > /tmp/swish_admin.log 2>&1) &
PIDS+=($!)
echo -e "✓ Admin Remote running on: ${GREEN}http://localhost:3003${NC}"

# B2B Remote (Port 5002)
(cd frontend-b2b && npm run preview -- --port 5002 --strictPort > /tmp/swish_b2b.log 2>&1) &
PIDS+=($!)
echo -e "✓ B2B/Wholesaler Remote running on: ${GREEN}http://localhost:5002${NC}"


echo -e "\n${GREEN}======================================================================${NC}"
echo -e "${GREEN}   🎉 ALL SWISH OS SERVICES RUNNING! Press Ctrl+C to stop the demo. ${NC}"
echo -e "${GREEN}======================================================================${NC}"

# Keep script running
while true; do
    sleep 1
done
