#!/usr/bin/env bash
# Проверка Telegram push для демо.
#
# Нужно один раз:
#   1. Создать бота через @BotFather → TELEGRAM_BOT_TOKEN
#   2. Написать боту любое сообщение в Telegram
#   3. Узнать chat id: ./infra/demo-test-telegram.sh --chat-id
#
# Полный прогон:
#   TELEGRAM_BOT_TOKEN=123:ABC TELEGRAM_CHAT_ID=987654321 ./infra/demo-test-telegram.sh
#
# Затем перезапустить backend:
#   TELEGRAM_ENABLED=true TELEGRAM_BOT_TOKEN=... ./infra/run-backend-demo.sh
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="${BACKEND_BASE_URL:-http://localhost:8081}"

usage() {
  cat <<'EOF'
Usage:
  TELEGRAM_BOT_TOKEN=... ./infra/demo-test-telegram.sh --chat-id
  TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... ./infra/demo-test-telegram.sh

Steps:
  1. Create bot via @BotFather
  2. Open chat with bot, press Start / send "hi"
  3. Run --chat-id to see your chat id
  4. Run full script → subscription + disk alert → check Telegram
  5. Restart backend with TELEGRAM_ENABLED=true and same token
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "${1:-}" == "--chat-id" ]]; then
  TOKEN="${TELEGRAM_BOT_TOKEN:-}"
  if [[ -z "$TOKEN" ]]; then
    echo "ERROR: set TELEGRAM_BOT_TOKEN"
    exit 1
  fi
  echo "Fetching updates (send a message to your bot first)..."
  RESPONSE="$(curl -sS "https://api.telegram.org/bot${TOKEN}/getUpdates")"
  if [[ -z "$RESPONSE" ]]; then
    echo "ERROR: empty response from Telegram API (network?)"
    exit 1
  fi
  printf '%s' "$RESPONSE" | python3 -c '
import json, sys
data = json.load(sys.stdin)
if not data.get("ok"):
    print("Telegram API error:", data)
    sys.exit(1)
results = data.get("result") or []
if not results:
    print("No messages yet. Open Telegram → your bot → press Start → run again.")
    sys.exit(1)
seen = {}
for item in reversed(results):
    msg = item.get("message") or item.get("edited_message") or {}
    chat = msg.get("chat") or {}
    cid = chat.get("id")
    if cid is None or cid in seen:
        continue
    seen[cid] = True
    title = chat.get("title") or " ".join(
        x for x in [chat.get("first_name"), chat.get("last_name"), chat.get("username")] if x
    )
    print(f"chat_id={cid}  ({title})")
'
  exit 0
fi

TOKEN="${TELEGRAM_BOT_TOKEN:-}"
CHAT_ID="${TELEGRAM_CHAT_ID:-}"
if [[ -z "$TOKEN" || -z "$CHAT_ID" ]]; then
  usage
  exit 1
fi

echo "==> 1/4 Check backend..."
if ! curl -sf --max-time 5 "${BACKEND}/api/public/app-config" >/dev/null; then
  echo "ERROR: backend not running at ${BACKEND}"
  echo "Start in another terminal:"
  echo "  TELEGRAM_ENABLED=true TELEGRAM_BOT_TOKEN=<token> ./infra/run-backend-demo.sh"
  exit 1
fi
echo "OK  backend reachable"

echo "==> 2/4 Create OPERATOR subscription (tag arm-workstation)..."
TELEGRAM_BOT_TOKEN="$TOKEN" TELEGRAM_CHAT_ID="$CHAT_ID" "$ROOT/infra/demo-setup-notifications.sh"

echo "==> 3/4 Send sample ARM alert via backend (Telegram)..."
OPERATOR_TOKEN="$(curl -sS -X POST "${BACKEND}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"operator@example.com","password":"operator123","authMode":"LOCAL"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")"
HTTP="$(curl -s -o /tmp/tg-test-event.json -w "%{http_code}" -X POST \
  "${BACKEND}/api/admin/system/notification-subscriptions/test-event" \
  -H "Authorization: Bearer ${OPERATOR_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"notificationKind":"OPERATOR","eventCode":"MONITORING_EVENT_OPEN","deviceIp":"192.168.64.11","deviceName":"pilot-linux-01","severity":"HIGH","metricName":"arm.disk.root.used_pct","deviceTags":"arm-workstation"}')"
if [[ "$HTTP" != "200" && "$HTTP" != "204" ]]; then
  echo "WARN test-event HTTP ${HTTP}: $(cat /tmp/tg-test-event.json)"
  echo "Fallback: direct Telegram ping..."
  ENC_TEXT="$(python3 -c 'import urllib.parse; print(urllib.parse.quote("WISLA АРМ: проверка канала (fallback)"))')"
  curl -sf "https://api.telegram.org/bot${TOKEN}/sendMessage?chat_id=${CHAT_ID}&text=${ENC_TEXT}" >/dev/null
else
  echo "OK  sample alert dispatched — проверьте Telegram"
fi

echo "==> 4/4 Trigger disk alert on pilot-linux-01 (live ingest)..."
"$ROOT/infra/demo-trigger-alert-resolve.sh" pilot-linux-01 >/dev/null 2>&1 || true
sleep 1
if [[ "${SKIP_ALERT:-}" != "1" ]]; then
  "$ROOT/infra/demo-trigger-disk-alert.sh" pilot-linux-01
fi

cat <<EOF

Done.

If Telegram is silent after step 4:
  • Backend must be restarted WITH telegram env:
      TELEGRAM_ENABLED=true TELEGRAM_BOT_TOKEN=${TOKEN} ./infra/run-backend-demo.sh
  • Then repeat: ./infra/demo-trigger-disk-alert.sh pilot-linux-01

UI fallback: http://localhost:3000 → События → OPEN

EOF
