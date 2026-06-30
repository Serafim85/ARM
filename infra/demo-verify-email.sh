#!/usr/bin/env bash
# Проверка: SMTP доходит до Яндекса? (без алерта)
#
#   ./infra/demo-verify-email.sh
#
set -euo pipefail
BACKEND="${BACKEND_BASE_URL:-http://localhost:8081}"
TO="${SMTP_TO:-ingeborg21@yandex.ru}"

TOKEN=$(curl -sf -X POST "$BACKEND/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"password","authMode":"LOCAL"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

echo "SMTP test → ${TO}"
HTTP=$(curl -s -o /tmp/arm-smtp-verify.json -w "%{http_code}" -X PUT "$BACKEND/api/admin/system/smtp-settings/test" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"recipientEmail\":\"${TO}\"}")

if [[ "$HTTP" == "200" || "$HTTP" == "204" ]]; then
  echo "OK  письмо отправлено — проверьте Входящие и Спам"
  exit 0
fi

echo "FAIL HTTP ${HTTP}"
python3 -c "import json; print(json.load(open('/tmp/arm-smtp-verify.json')).get('message',''))" 2>/dev/null || cat /tmp/arm-smtp-verify.json
echo ""
cat <<'EOF'
Чаще всего: Authentication failed

• Новый пароль приложения Яндекса действует только через 2–3 часа — подождите и повторите.

1. id.yandex.ru → Безопасность → Пароли приложений → создать НОВЫЙ
2. Почта → Настройки → Почтовые программы:
   ✓ С сервера imap.yandex.ru через IMAP
   ✓ Пароли приложений и OAuth-токены
3. Обновить SMTP_PASS в infra/notifications.env
4. ./infra/demo-setup-alerts.sh
5. Снова ./infra/demo-verify-email.sh

Альтернатива (если 465 не работает) в notifications.env:
  SMTP_PORT=587
  SMTP_SSL=false
  SMTP_STARTTLS=true
EOF
exit 1
