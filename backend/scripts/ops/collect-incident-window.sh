#!/usr/bin/env bash
set -euo pipefail

SINCE="${1:-2026-04-18 07:00:00}"
UNTIL="${2:-2026-04-18 08:00:00}"
OUT_DIR="${OUT_DIR:-./incident-$(date +%Y%m%d-%H%M%S)}"
BACKEND_SERVICE="${BACKEND_SERVICE:-networkscanner-backend}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-networkscanner-kafka}"
DB_CONTAINER="${DB_CONTAINER:-networkscanner-timescaledb}"

mkdir -p "${OUT_DIR}"

echo "Collecting diagnostics to ${OUT_DIR}"
echo "Window: ${SINCE} .. ${UNTIL}"

journalctl -u "${BACKEND_SERVICE}" --since "${SINCE}" --until "${UNTIL}" --no-pager \
  > "${OUT_DIR}/backend-journal.log" || true
journalctl -k --since "${SINCE}" --until "${UNTIL}" --no-pager \
  > "${OUT_DIR}/kernel-journal.log" || true
dmesg -T > "${OUT_DIR}/dmesg.log" || true

docker logs "${KAFKA_CONTAINER}" --since "${SINCE}" --until "${UNTIL}" \
  > "${OUT_DIR}/kafka.log" 2>&1 || true
docker logs "${DB_CONTAINER}" --since "${SINCE}" --until "${UNTIL}" \
  > "${OUT_DIR}/timescaledb.log" 2>&1 || true

{
  echo "== vmstat =="
  vmstat 1 10
  echo
  echo "== iostat =="
  iostat -xz 1 5
} > "${OUT_DIR}/host-load.txt" 2>&1 || true

{
  echo "== docker stats snapshot =="
  docker stats --no-stream
} > "${OUT_DIR}/docker-stats.txt" 2>&1 || true

echo "Done. Files:"
ls -1 "${OUT_DIR}"
