#!/usr/bin/env bash
# Apply max.message.bytes to monitoring pipeline topics on an existing cluster.
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9094}"
MAX_BYTES="${KAFKA_MAX_MESSAGE_BYTES:-2097152}"
TOPIC_POLLED="${TOPIC_POLLED:-monitoring.polled}"
TOPIC_EVALUATED="${TOPIC_EVALUATED:-monitoring.evaluated}"

for topic in "${TOPIC_POLLED}" "${TOPIC_EVALUATED}" "${TOPIC_POLLED}.DLT" "${TOPIC_EVALUATED}.DLT"; do
  echo "Setting max.message.bytes=${MAX_BYTES} on topic ${topic}"
  kafka-configs.sh --bootstrap-server "${BOOTSTRAP}" \
    --entity-type topics \
    --entity-name "${topic}" \
    --alter \
    --add-config "max.message.bytes=${MAX_BYTES}"
done

echo "Done."
