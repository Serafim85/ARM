#!/usr/bin/env bash
# Configure Telegram and/or SMTP subscriptions for ARM demo alerts.
#
# Рекомендуется одна команда для обоих каналов:
#   cp infra/notifications.env.example infra/notifications.env
#   ./infra/demo-setup-alerts.sh
#
# Или вручную:
#   TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... ./infra/demo-setup-notifications.sh
#   SMTP_HOST=smtp.yandex.ru SMTP_PASS=... SMTP_TO=ingeborg21@yandex.ru ./infra/demo-setup-notifications.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="${BACKEND_BASE_URL:-http://localhost:8081}"
EMAIL="${DEMO_ADMIN_EMAIL:-admin@example.com}"
PASS="${DEMO_ADMIN_PASSWORD:-password}"
OPERATOR_EMAIL="${DEMO_OPERATOR_EMAIL:-operator@example.com}"
OPERATOR_PASS="${DEMO_OPERATOR_PASSWORD:-operator123}"

require_backend() {
  if ! curl -sf --max-time 5 "${BACKEND}/api/public/app-config" >/dev/null; then
    echo "ERROR: backend not reachable at ${BACKEND}" >&2
    echo "Start: ./infra/run-backend-demo.sh" >&2
    echo "Or with Telegram: TELEGRAM_ENABLED=true TELEGRAM_BOT_TOKEN=... ./infra/run-backend-demo.sh" >&2
    exit 1
  fi
}

login() {
  local user_email="$1"
  local user_pass="$2"
  local resp http body
  resp="$(curl -sS -w $'\n%{http_code}' -X POST "${BACKEND}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${user_email}\",\"password\":\"${user_pass}\",\"authMode\":\"LOCAL\"}")"
  http="${resp##*$'\n'}"
  body="${resp%$'\n'*}"
  if [[ "$http" != "200" ]]; then
    echo "ERROR: login failed for ${user_email} (HTTP ${http})" >&2
    [[ -n "$body" ]] && echo "$body" >&2
    exit 1
  fi
  printf '%s' "$body" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])"
}

require_backend

TOKEN="$(login "$EMAIL" "$PASS")"
AUTH="Authorization: Bearer ${TOKEN}"

create_subscription() {
  local bearer_auth="$1"
  local channel="$2"
  local recipient="$3"
  local payload
  payload="$(python3 - <<PY
import json, os
print(json.dumps({
  "enabled": True,
  "notificationKind": "OPERATOR",
  "subscriptionType": "TAG_GROUP",
  "channel": os.environ["CHANNEL"],
  "eventCodes": ["MONITORING_EVENT_OPEN", "MONITORING_EVENT_RESOLVED"],
  "recipientEmail": os.environ["RECIPIENT"],
  "deviceTagFilter": "arm-workstation"
}))
PY
)"
  local http body
  http="$(curl -sS -o /tmp/arm-sub.json -w "%{http_code}" -X POST "${BACKEND}/api/admin/system/notification-subscriptions" \
    -H "${bearer_auth}" -H "Content-Type: application/json" \
    -d "${payload}")"
  body="$(cat /tmp/arm-sub.json)"
  if [[ "$http" != "200" ]]; then
    echo "ERROR: failed to create ${channel} subscription (HTTP ${http}): ${body}" >&2
    return 1
  fi
  echo "OK ${channel} subscription → ${recipient}"
}

if [[ -n "${SMTP_HOST:-}" && -n "${SMTP_TO:-}" ]]; then
  echo "Configuring SMTP..."
  curl -sf -X PUT "${BACKEND}/api/admin/system/smtp-settings" \
    -H "${AUTH}" -H "Content-Type: application/json" \
    -d "$(python3 - <<PY
import json, os
print(json.dumps({
  "enabled": True,
  "serverHost": os.environ["SMTP_HOST"],
  "serverPort": int(os.environ.get("SMTP_PORT", "587")),
  "auth": True,
  "starttls": os.environ.get("SMTP_STARTTLS", "true").lower() == "true",
  "ssl": os.environ.get("SMTP_SSL", "false").lower() == "true",
  "username": os.environ.get("SMTP_USER", ""),
  "password": os.environ.get("SMTP_PASS", ""),
  "clearPassword": False,
  "fromEmail": os.environ.get("SMTP_FROM", os.environ.get("SMTP_USER", os.environ["SMTP_TO"]))
}))
PY
)" >/dev/null

  OPERATOR_TOKEN="$(login "$OPERATOR_EMAIL" "$OPERATOR_PASS")"
  OPERATOR_AUTH="Authorization: Bearer ${OPERATOR_TOKEN}"

  EXISTING_SMTP="$(curl -sf "${BACKEND}/api/admin/system/notification-subscriptions" \
    -H "${OPERATOR_AUTH}" \
    | python3 -c "
import json, os, sys
to = os.environ['SMTP_TO'].strip().lower()
subs = json.load(sys.stdin)
for s in subs:
    if (s.get('channel') or '').upper() == 'SMTP' and (s.get('recipientEmail') or '').strip().lower() == to:
        print(s.get('id'))
        break
" SMTP_TO="$SMTP_TO")"
  if [[ -n "$EXISTING_SMTP" ]]; then
    echo "SMTP subscription already exists (id ${EXISTING_SMTP}, ${SMTP_TO})."
  else
    CHANNEL=SMTP RECIPIENT="$SMTP_TO" create_subscription "$OPERATOR_AUTH" SMTP "$SMTP_TO"
  fi
  echo "Test: UI → Настройки → SMTP → «Отправить тест»"
fi

if [[ -n "${TELEGRAM_BOT_TOKEN:-}" && -n "${TELEGRAM_CHAT_ID:-}" ]]; then
  echo "Configuring Telegram..."
  OPERATOR_TOKEN="$(login "$OPERATOR_EMAIL" "$OPERATOR_PASS")"
  OPERATOR_AUTH="Authorization: Bearer ${OPERATOR_TOKEN}"
  EXISTING="$(curl -sf "${BACKEND}/api/admin/system/notification-subscriptions" \
    -H "${OPERATOR_AUTH}" \
    | python3 -c "
import json, os, sys
chat = os.environ['TELEGRAM_CHAT_ID'].strip()
subs = json.load(sys.stdin)
for s in subs:
    if (s.get('channel') or '').upper() == 'TELEGRAM' and s.get('recipientEmail') == chat:
        print(s.get('id'))
        break
" TELEGRAM_CHAT_ID="$TELEGRAM_CHAT_ID")"
  if [[ -n "$EXISTING" ]]; then
    echo "Telegram subscription already exists (id ${EXISTING}, chat ${TELEGRAM_CHAT_ID})."
  else
    CHANNEL=TELEGRAM RECIPIENT="$TELEGRAM_CHAT_ID" create_subscription "$OPERATOR_AUTH" TELEGRAM "$TELEGRAM_CHAT_ID"
  fi
  echo "Restart backend with: TELEGRAM_ENABLED=true TELEGRAM_BOT_TOKEN=... ./infra/run-backend-demo.sh"
fi

if [[ -z "${SMTP_HOST:-}" && -z "${TELEGRAM_BOT_TOKEN:-}" ]]; then
  echo "No SMTP_HOST or TELEGRAM_BOT_TOKEN set."
  echo "Alerts still appear in UI → События. Set env vars above for push notifications."
  exit 0
fi

echo "Done."
echo "  Fire alert (Telegram + email + UI): ./infra/demo-fire-alert.sh pilot-linux-01"
