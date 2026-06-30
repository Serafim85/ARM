#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVERS="${BOOTSTRAP_SERVERS:-10.11.11.105:9094}"
TOPIC_POLLED="${TOPIC_POLLED:-monitoring.polled}"
TOPIC_EVALUATED="${TOPIC_EVALUATED:-monitoring.evaluated}"
GROUP_EVAL="${GROUP_EVAL:-cg_eval}"
GROUP_WRITER="${GROUP_WRITER:-cg_writer}"

if ! command -v kafka-consumer-groups.sh >/dev/null 2>&1; then
  echo "kafka-consumer-groups.sh is not found in PATH" >&2
  exit 1
fi

echo "== $(date -Is) Kafka lag snapshot =="
echo "bootstrap: ${BOOTSTRAP_SERVERS}"
echo

echo "-- ${GROUP_EVAL} (${TOPIC_POLLED}) --"
kafka-consumer-groups.sh \
  --bootstrap-server "${BOOTSTRAP_SERVERS}" \
  --group "${GROUP_EVAL}" \
  --describe || true
echo

echo "-- ${GROUP_WRITER} (${TOPIC_EVALUATED}) --"
kafka-consumer-groups.sh \
  --bootstrap-server "${BOOTSTRAP_SERVERS}" \
  --group "${GROUP_WRITER}" \
  --describe || true
echo

echo "-- topic high-level metrics --"
kafka-consumer-groups.sh \
  --bootstrap-server "${BOOTSTRAP_SERVERS}" \
  --all-groups \
  --describe | awk '
  NR==1 || /cg_eval|cg_writer/ { print }
'
