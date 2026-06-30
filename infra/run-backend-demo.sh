#!/usr/bin/env bash
# Demo day backend: faster offline detection (2 min instead of 10).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -f "$ROOT/infra/telegram.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/infra/telegram.env"
  set +a
fi
export WORKSTATION_OFFLINE_THRESHOLD_MINUTES="${WORKSTATION_OFFLINE_THRESHOLD_MINUTES:-2}"
export WORKSTATION_OFFLINE_CHECK_MS="${WORKSTATION_OFFLINE_CHECK_MS:-60000}"
export TELEGRAM_ENABLED="${TELEGRAM_ENABLED:-false}"
cd "$ROOT"
"$ROOT/infra/ensure-docker.sh" --up
echo "Demo backend: offline threshold=${WORKSTATION_OFFLINE_THRESHOLD_MINUTES}m, offline check every ${WORKSTATION_OFFLINE_CHECK_MS}ms"
if [[ "$TELEGRAM_ENABLED" != "true" || -z "${TELEGRAM_BOT_TOKEN:-}" ]]; then
  echo "WARN: Telegram push disabled (set TELEGRAM_ENABLED=true + TELEGRAM_BOT_TOKEN, or create infra/telegram.env)"
else
  echo "Telegram push: enabled"
fi
exec ./infra/run-backend-dev.sh "$@"
