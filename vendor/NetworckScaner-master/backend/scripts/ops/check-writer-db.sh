#!/usr/bin/env bash
set -euo pipefail

SINCE="${SINCE:-1 hour ago}"
UNTIL="${UNTIL:-now}"
SERVICE_NAME="${SERVICE_NAME:-networkscanner-backend}"
DB_CONTAINER="${DB_CONTAINER:-networkscanner-timescaledb}"

echo "== $(date -Is) writer/db diagnostics =="
echo "window: ${SINCE} .. ${UNTIL}"
echo

echo "-- backend writer-related logs --"
journalctl -u "${SERVICE_NAME}" --since "${SINCE}" --until "${UNTIL}" --no-pager \
  | rg "MonitoringWriterKafkaListener|MonitoringWriterServiceImpl|Hikari|ERROR|Exception" || true
echo

echo "-- timescaledb container logs --"
docker logs "${DB_CONTAINER}" --since "${SINCE}" --until "${UNTIL}" 2>&1 \
  | rg -i "error|fatal|panic|checkpoint|timeout|canceling statement|too many connections" || true
echo

echo "-- active postgres backends in container --"
docker exec "${DB_CONTAINER}" psql -U networkscanner -d networkscanner -c \
  "select now(), state, wait_event_type, wait_event, count(*) from pg_stat_activity group by 1,2,3,4 order by count(*) desc;" || true
