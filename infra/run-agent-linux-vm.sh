#!/usr/bin/env bash
# Run wisla-arm-agent on Linux VM from ~/Agent
# Usage on VM: chmod +x run-agent.sh && ./run-agent.sh
# Set LINUX_VM_IP before first run if needed.

export WISLA_ARM_AGENT_KEY="${WISLA_ARM_AGENT_KEY:-dev-arm-ingest-key}"
export WISLA_ARM_SERVER_URL="${WISLA_ARM_SERVER_URL:-http://192.168.0.118:8081}"
export WISLA_ARM_HOSTNAME="${WISLA_ARM_HOSTNAME:-pilot-linux-01}"
export WISLA_ARM_OS_TYPE="${WISLA_ARM_OS_TYPE:-linux}"
export WISLA_ARM_PRIMARY_IP="${WISLA_ARM_PRIMARY_IP:-$(hostname -I 2>/dev/null | awk '{print $1}')}"
export WISLA_ARM_POLL_INTERVAL_SEC="${WISLA_ARM_POLL_INTERVAL_SEC:-30}"

AGENT_DIR="$(cd "$(dirname "$0")" && pwd)"
AGENT_BIN="${AGENT_DIR}/wisla-arm-agent-linux"

if [[ ! -x "$AGENT_BIN" ]]; then
  echo "ERROR: not found or not executable: $AGENT_BIN"
  echo "Copy from Mac: scp .../agent/dist/wisla-arm-agent-linux user@vm:~/Agent/"
  exit 1
fi

echo "Starting agent host=$WISLA_ARM_HOSTNAME server=$WISLA_ARM_SERVER_URL ip=$WISLA_ARM_PRIMARY_IP"
exec "$AGENT_BIN"
