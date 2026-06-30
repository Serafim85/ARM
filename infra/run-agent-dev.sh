#!/usr/bin/env bash
# Run Go agent against local backend (backend must be up).
# Real host metrics by default. Stub: WISLA_ARM_USE_STUB=1 ...
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export WISLA_ARM_AGENT_KEY="${WISLA_ARM_AGENT_KEY:-dev-arm-ingest-key}"
export WISLA_ARM_SERVER_URL="${WISLA_ARM_SERVER_URL:-http://localhost:8081}"
export WISLA_ARM_POLL_INTERVAL_SEC="${WISLA_ARM_POLL_INTERVAL_SEC:-30}"
cd "$ROOT/agent"
exec go run ./cmd/wisla-arm-agent
