#!/usr/bin/env bash
# Fire disk-critical threshold on a workstation (instant alert in UI + notifications).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=demo-alert-common.sh
source "$ROOT/infra/demo-alert-common.sh"
HOSTNAME="${1:-pilot-linux-01}"
BACKEND="${BACKEND_BASE_URL:-http://localhost:8081}"
KEY="${AGENT_INGEST_API_KEY:-dev-arm-ingest-key}"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

BODY="$(demo_ingest_body "$ROOT/tests/fixtures/ingest-batch-disk-critical.json" "$HOSTNAME" "$NOW")"

echo "Triggering disk CRITICAL on ${HOSTNAME} via ingest..."
RESP="$(curl -s -w "\n%{http_code}" -X POST "${BACKEND}/api/v1/agent/ingest" \
  -H "X-Agent-Key: ${KEY}" \
  -H "Content-Type: application/json" \
  -d "${BODY}")"
HTTP_CODE="$(echo "$RESP" | tail -1)"
BODY_OUT="$(echo "$RESP" | sed '$d')"
if [[ "$HTTP_CODE" != "200" ]]; then
  echo "FAIL HTTP ${HTTP_CODE}: ${BODY_OUT}"
  exit 1
fi
echo "OK  ${BODY_OUT}"
echo ""
if TG_READY="$(curl -sf --max-time 3 "${BACKEND}/api/public/app-config" 2>/dev/null \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('telegramReady', False))" 2>/dev/null)" \
  && [[ "$TG_READY" != "True" && "$TG_READY" != "true" ]]; then
  echo "WARN: Backend Telegram is OFF — push will NOT arrive in Telegram."
  echo "      Restart backend: TELEGRAM_ENABLED=true TELEGRAM_BOT_TOKEN=... ./infra/run-backend-demo.sh"
  echo "      (or copy infra/telegram.env.example → infra/telegram.env)"
  echo ""
fi
echo "Check UI: События → OPEN, filter tag arm-workstation"
echo "Expected trigger: ARM Linux: Root disk space critical (>95%)"
