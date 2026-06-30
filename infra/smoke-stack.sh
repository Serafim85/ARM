#!/usr/bin/env bash
# WISLA ARM — MVP smoke: DB → backend → ingest → DB rows
# Usage: ./infra/smoke-stack.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml")
API="${BACKEND_BASE_URL:-http://localhost:8081}"
AGENT_KEY="${AGENT_INGEST_API_KEY:-dev-arm-ingest-key}"
HOSTNAME="smoke-$(date +%s)"
LOG="$ROOT/infra/.smoke-backend.log"
PIDFILE="$ROOT/infra/.smoke-backend.pid"

cleanup() {
  if [[ -f "$PIDFILE" ]]; then
    local pid
    pid="$(cat "$PIDFILE")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "Stopping backend (pid $pid)..."
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
    rm -f "$PIDFILE"
  fi
}
trap cleanup EXIT

wait_http() {
  local url="$1"
  local attempts="${2:-60}"
  for i in $(seq 1 "$attempts"); do
    if curl -sf "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "Timeout waiting for $url"
  return 1
}

echo "=== 1. TimescaleDB ==="
if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker is not running. Start Docker Desktop and retry."
  exit 1
fi
"${COMPOSE[@]}" up -d
"${COMPOSE[@]}" ps

echo "Waiting for PostgreSQL..."
for i in $(seq 1 30); do
  if docker exec wisla-arm-timescaledb pg_isready -U wisla_arm -d wisla_arm >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker exec wisla-arm-timescaledb pg_isready -U wisla_arm -d wisla_arm

echo "=== 2. Backend (profile wisla-arm) ==="
if curl -sf "$API/api/public/app-config" >/dev/null 2>&1; then
  echo "Backend already up at $API"
else
  rm -f "$LOG"
  (cd "$ROOT/backend" && mvn -q spring-boot:run -Dspring-boot.run.profiles=wisla-arm) >>"$LOG" 2>&1 &
  echo $! >"$PIDFILE"
  echo "Starting backend (log: $LOG)..."
  wait_http "$API/api/public/app-config" 90
fi

echo "=== 3. Agent ingest ==="
TS="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
PAYLOAD=$(cat <<EOF
{
  "hostname": "$HOSTNAME",
  "timestamp": "$TS",
  "agent_version": "0.1.0-smoke",
  "os_type": "linux",
  "primary_ip": "10.99.0.1",
  "metrics": [
    {"key": "arm.cpu.util", "value": 42.0, "clock": "$TS"},
    {"key": "arm.mem.used", "value": 1073741824, "clock": "$TS"}
  ],
  "logs": [
    {"level": "warning", "message": "smoke test log", "clock": "$TS", "source": "smoke-stack.sh"}
  ]
}
EOF
)

RESP=$(curl -sf -X POST "$API/api/v1/agent/ingest" \
  -H "Content-Type: application/json" \
  -H "X-Agent-Key: $AGENT_KEY" \
  -d "$PAYLOAD" -w "\nHTTP_CODE:%{http_code}")
HTTP_CODE=$(echo "$RESP" | sed -n 's/^HTTP_CODE://p')
HTTP_BODY=$(echo "$RESP" | sed '/^HTTP_CODE:/d')

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "Ingest failed HTTP ${HTTP_CODE:-unknown}: $HTTP_BODY"
  tail -30 "$LOG" 2>/dev/null || true
  exit 1
fi
echo "Ingest response: $HTTP_BODY"

echo "=== 4. DB verification ==="
WS_COUNT=$(docker exec wisla-arm-timescaledb psql -U wisla_arm -d wisla_arm -t -A -c \
  "SELECT count(*) FROM workstations WHERE hostname = '$HOSTNAME';")
METRIC_COUNT=$(docker exec wisla-arm-timescaledb psql -U wisla_arm -d wisla_arm -t -A -c \
  "SELECT count(*) FROM metric_values WHERE device_ip = '10.99.0.1' AND metric_name = 'arm.cpu.util';")
LOG_COUNT=$(docker exec wisla-arm-timescaledb psql -U wisla_arm -d wisla_arm -t -A -c \
  "SELECT count(*) FROM arm_log_events le JOIN workstations w ON w.id = le.workstation_id WHERE w.hostname = '$HOSTNAME';")

echo "workstations: $WS_COUNT (expect 1)"
echo "metric_values (arm.cpu.util): $METRIC_COUNT (expect >= 1)"
echo "arm_log_events: $LOG_COUNT (expect >= 1)"

if [[ "$WS_COUNT" != "1" ]] || [[ "$METRIC_COUNT" -lt 1 ]] || [[ "$LOG_COUNT" -lt 1 ]]; then
  echo "SMOKE FAILED — check backend log: $LOG"
  exit 1
fi

echo "=== SMOKE OK ==="
echo "Next: cd agent && WISLA_ARM_AGENT_KEY=$AGENT_KEY go run ./cmd/wisla-arm-agent"
