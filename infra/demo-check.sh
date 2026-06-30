#!/usr/bin/env bash
# Preflight before demo: DB, backend, frontend, ingest.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="${BACKEND_BASE_URL:-http://localhost:8081}"
FRONTEND="${E2E_BASE_URL:-http://localhost:3000}"
AGENT_KEY="${AGENT_INGEST_API_KEY:-dev-arm-ingest-key}"
FAIL=0

check() {
  local name="$1" url="$2"
  if curl -sf --max-time 5 "$url" >/dev/null; then
    echo "OK  $name  $url"
  else
    echo "FAIL $name  $url"
    FAIL=1
  fi
}

echo "=== WISLA ARM demo preflight ==="
check "PostgreSQL (via backend)" "$BACKEND/api/public/app-config"
check "Frontend" "$FRONTEND"

echo "--- ingest probe ---"
HTTP_CODE=$(curl -s -o /tmp/arm-demo-ingest.json -w "%{http_code}" \
  -X POST "$BACKEND/api/v1/agent/ingest" \
  -H "X-Agent-Key: $AGENT_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"hostname\":\"demo-preflight\",\"timestamp\":\"2026-06-24T12:00:00Z\",\"agent_version\":\"0.1.0-dev\",\"os_type\":\"linux\",\"metrics\":[{\"key\":\"arm.cpu.util\",\"value\":1.0}],\"logs\":[],\"events\":[]}")

if [[ "$HTTP_CODE" == "200" ]]; then
  echo "OK  ingest  HTTP $HTTP_CODE  $(cat /tmp/arm-demo-ingest.json)"
else
  echo "FAIL ingest  HTTP $HTTP_CODE"
  FAIL=1
fi

if command -v ipconfig >/dev/null 2>&1; then
  MAC_IP=$(ipconfig getifaddr en0 2>/dev/null || true)
  if [[ -n "${MAC_IP:-}" ]]; then
    echo "--- VM agents should use ---"
    echo "  WISLA_ARM_SERVER_URL=http://${MAC_IP}:8081"
  fi
fi

if [[ $FAIL -eq 0 ]]; then
  echo "=== PREFLIGHT OK ==="
else
  echo "=== PREFLIGHT FAILED — fix before demo ==="
  exit 1
fi
