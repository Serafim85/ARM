#!/usr/bin/env bash
set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-networkscanner-timescaledb}"
PSQL_USER="${PSQL_USER:-networkscanner}"
PSQL_DB="${PSQL_DB:-networkscanner}"

psql_exec() {
  docker exec "${DB_CONTAINER}" psql -U "${PSQL_USER}" -d "${PSQL_DB}" -v ON_ERROR_STOP=1 -c "$1"
}

echo "== $(date -Is) metric_values storage check =="
echo

echo "-- hypertable sizes --"
psql_exec "
SELECT hypertable_name,
       pg_size_pretty(hypertable_size(format('%I.%I', hypertable_schema, hypertable_name)::regclass)) AS total_size
FROM timescaledb_information.hypertables
WHERE hypertable_name IN ('metric_values', 'metric_values_1h', 'monitoring_events', 'availability_history', 'telemetry_history')
ORDER BY hypertable_name;
"

echo "-- metric_values row stats --"
psql_exec "
SELECT COUNT(*) AS rows,
       COUNT(DISTINCT device_ip) AS devices,
       COUNT(DISTINCT metric_name) AS metrics,
       MIN(recorded_at) AS oldest,
       MAX(recorded_at) AS newest
FROM metric_values;
"

echo "-- top devices by row count --"
psql_exec "
SELECT device_ip, COUNT(*) AS rows, COUNT(DISTINCT metric_name) AS metrics
FROM metric_values
GROUP BY device_ip
ORDER BY rows DESC
LIMIT 10;
"

echo "-- orphan metric_values (IP not on monitoring) --"
psql_exec "
SELECT COUNT(*) AS orphan_rows
FROM metric_values mv
WHERE NOT EXISTS (SELECT 1 FROM monitored_devices d WHERE d.ip = mv.device_ip);
"

echo "-- Timescale jobs (retention/compression/refresh) --"
psql_exec "
SELECT job_id, application_name, schedule_interval, config
FROM timescaledb_information.jobs
WHERE proc_name IN ('policy_retention', 'policy_compression', 'policy_refresh_continuous_aggregate')
ORDER BY job_id;
"

echo "-- metric_values_1h row stats --"
psql_exec "
SELECT COUNT(*) AS rows,
       COUNT(DISTINCT device_ip) AS devices,
       MIN(bucket) AS oldest,
       MAX(bucket) AS newest
FROM metric_values_1h;
"

echo "-- compression on metric_values chunks --"
psql_exec "
SELECT COUNT(*) FILTER (WHERE is_compressed) AS compressed_chunks,
       COUNT(*) FILTER (WHERE NOT is_compressed) AS uncompressed_chunks
FROM timescaledb_information.chunks
WHERE hypertable_name = 'metric_values';
"
