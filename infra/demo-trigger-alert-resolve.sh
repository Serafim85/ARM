#!/usr/bin/env bash
# Resolve disk alert by sending normal disk usage.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=demo-alert-common.sh
source "$ROOT/infra/demo-alert-common.sh"
HOSTNAME="${1:-pilot-linux-01}"
BACKEND="${BACKEND_BASE_URL:-http://localhost:8081}"
KEY="${AGENT_INGEST_API_KEY:-dev-arm-ingest-key}"
NOW="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

BODY="$(demo_ingest_body "$ROOT/tests/fixtures/ingest-batch-disk-ok.json" "$HOSTNAME" "$NOW")"

echo "Resolving disk alert on ${HOSTNAME}..."
curl -sf -X POST "${BACKEND}/api/v1/agent/ingest" \
  -H "X-Agent-Key: ${KEY}" \
  -H "Content-Type: application/json" \
  -d "${BODY}" >/dev/null
echo "OK — disk back to normal. Event should move to RESOLVED in События."
echo ""
echo "Для push «🔴 Сработало событие» в Telegram сразу после этого:"
echo "  ./infra/demo-trigger-disk-alert.sh ${HOSTNAME}"
