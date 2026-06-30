#!/usr/bin/env bash
# Устарело: используйте ./infra/demo-setup-alerts.sh (Telegram + почта)
exec "$(cd "$(dirname "$0")/.." && pwd)/infra/demo-setup-alerts.sh" "$@"
