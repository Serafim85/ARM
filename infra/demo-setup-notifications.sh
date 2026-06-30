#!/usr/bin/env bash
# Configure Telegram (or SMTP) subscriptions for ARM demo alerts.
# Usage:
#   TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=123456789 ./infra/demo-setup-notifications.sh
#   SMTP_HOST=smtp.gmail.com SMTP_USER=... SMTP_PASS=... SMTP_TO=ops@example.com ./infra/demo-setup-notifications.sh
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
  "ssl": False,
  "username": os.environ.get("SMTP_USER", ""),
  "password": os.environ.get("SMTP_PASS", ""),
  "clearPassword": False,
  "fromEmail": os.environ.get("SMTP_FROM", os.environ["SMTP_USER"])
}))
PY
)" >/dev/null
  curl -sf -X POST "${BACKEND}/api/admin/system/notification-subscriptions" \
    -H "${AUTH}" -H "Content-Type: application/json" \
    -d "$(python3 - <<PY
import json, os
print(json.dumps({
  "enabled": True,
  "notificationKind": "OPERATOR",
  "subscriptionType": "TAG_GROUP",
  "channel": "SMTP",
  "eventCodes": ["MONITORING_EVENT_OPEN", "MONITORING_EVENT_RESOLVED"],
  "recipientEmail": os.environ["SMTP_TO"],
  "deviceTagFilter": "arm-workstation"
}))
PY
)" >/dev/null
  echo "SMTP subscription for tag arm-workstation created."
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
    echo "Telegram subscription already exists (id ${EXISTING}, chat ${TELEGRAM_CHAT_ID}). Skipping duplicate."
  else
    curl -sf -X POST "${BACKEND}/api/admin/system/notification-subscriptions" \
      -H "${OPERATOR_AUTH}" -H "Content-Type: application/json" \
      -d "$(python3 - <<PY
import json, os
print(json.dumps({
  "enabled": True,
  "notificationKind": "OPERATOR",
  "subscriptionType": "TAG_GROUP",
  "channel": "TELEGRAM",
  "eventCodes": ["MONITORING_EVENT_OPEN", "MONITORING_EVENT_RESOLVED"],
  "recipientEmail": os.environ["TELEGRAM_CHAT_ID"],
  "deviceTagFilter": "arm-workstation"
}))
PY
)" >/dev/null || {
      echo "ERROR: failed to create Telegram subscription (is backend up to date?)" >&2
      exit 1
    }
    echo "Telegram subscription created (chat id ${TELEGRAM_CHAT_ID})."
  fi
  echo "Restart backend with: TELEGRAM_ENABLED=true TELEGRAM_BOT_TOKEN=... ./infra/run-backend-demo.sh"
fi

if [[ -z "${SMTP_HOST:-}" && -z "${TELEGRAM_BOT_TOKEN:-}" ]]; then
  echo "No SMTP_HOST or TELEGRAM_BOT_TOKEN set."
  echo "Alerts still appear in UI → События. Set env vars above for push notifications."
  exit 0
fi

echo "Done. Run: ./infra/demo-trigger-disk-alert.sh pilot-linux-01"
