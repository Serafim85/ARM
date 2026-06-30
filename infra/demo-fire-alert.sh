#!/usr/bin/env bash
# Одна команда: авария → push в Telegram + почту + запись в UI «События».
#
#   ./infra/demo-fire-alert.sh pilot-linux-01
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOSTNAME="${1:-pilot-linux-01}"
BACKEND="${BACKEND_BASE_URL:-http://localhost:8081}"

if [[ -f "$ROOT/infra/notifications.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/infra/notifications.env"
  set +a
elif [[ -f "$ROOT/infra/smtp.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/infra/smtp.env"
  set +a
fi
MAIL_TO="${SMTP_TO:-ingeborg21@yandex.ru}"

echo "==> 1/3 Сброс (resolve)..."
"$ROOT/infra/demo-trigger-alert-resolve.sh" "$HOSTNAME" || true

echo ""
echo "==> 2/3 Пауза 3 с (иначе порог видит старые 42% вместо 96.5%)..."
sleep 3

echo "==> 3/3 Критический диск → OPEN + уведомления..."
"$ROOT/infra/demo-trigger-disk-alert.sh" "$HOSTNAME"

TG_READY="$(curl -sf --max-time 3 "${BACKEND}/api/public/app-config" 2>/dev/null \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('telegramReady', False))" 2>/dev/null || echo false)"

OPEN_COUNT="$(docker exec wisla-arm-timescaledb psql -U wisla_arm -d wisla_arm -t -A -c \
  "SELECT count(*) FROM monitoring_events me
   JOIN monitored_devices md ON md.id = me.device_id
   WHERE md.host_name = '${HOSTNAME}' AND me.status = 'OPEN';" 2>/dev/null || echo "?")"

cat <<EOF

Готово.

  telegramReady: ${TG_READY}
  OPEN событий для ${HOSTNAME}: ${OPEN_COUNT}

Проверьте:
  • UI → События → OPEN
  • Telegram Web → бот

EOF

if [[ "${OPEN_COUNT}" == "0" ]]; then
  echo "WARN: OPEN не создан — повторите только триггер:"
  echo "  ./infra/demo-trigger-disk-alert.sh ${HOSTNAME}"
  exit 1
fi
