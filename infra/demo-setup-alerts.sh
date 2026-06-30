#!/usr/bin/env bash
# Одна команда: настроить Telegram + почту для алертов АРМ.
#
#   cp infra/notifications.env.example infra/notifications.env
#   # заполните токен, chat id, SMTP_PASS
#   ./infra/demo-setup-alerts.sh
#
# Прогон аварии (push в оба канала + UI «События»):
#   ./infra/demo-fire-alert.sh pilot-linux-01
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

load_env() {
  local f="$1"
  if [[ -f "$f" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$f"
    set +a
  fi
}

load_env "$ROOT/infra/notifications.env"
load_env "$ROOT/infra/telegram.env"
load_env "$ROOT/infra/smtp.env"

# Yandex defaults
export SMTP_HOST="${SMTP_HOST:-smtp.yandex.ru}"
export SMTP_PORT="${SMTP_PORT:-465}"
export SMTP_SSL="${SMTP_SSL:-true}"
export SMTP_STARTTLS="${SMTP_STARTTLS:-false}"
export SMTP_USER="${SMTP_USER:-ingeborg21@yandex.ru}"
export SMTP_FROM="${SMTP_FROM:-ingeborg21@yandex.ru}"
export SMTP_TO="${SMTP_TO:-ingeborg21@yandex.ru}"

missing=()
[[ -z "${TELEGRAM_BOT_TOKEN:-}" || -z "${TELEGRAM_CHAT_ID:-}" ]] && missing+=("Telegram: TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID")
[[ -z "${SMTP_HOST:-}" || -z "${SMTP_TO:-}" || -z "${SMTP_PASS:-}" ]] && missing+=("SMTP: SMTP_PASS (и SMTP_TO в notifications.env)")

if [[ ${#missing[@]} -eq 2 ]]; then
  echo "ERROR: заполните infra/notifications.env" >&2
  echo "  cp infra/notifications.env.example infra/notifications.env" >&2
  exit 1
fi

echo "==> Настройка каналов алертов"
for m in "${missing[@]}"; do
  echo "    SKIP $m"
done
echo ""

exec "$ROOT/infra/demo-setup-notifications.sh"
