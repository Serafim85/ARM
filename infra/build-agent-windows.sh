#!/usr/bin/env bash
# Cross-compile wisla-arm-agent for Windows amd64 (run on Mac/Linux dev host).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/agent/dist/wisla-arm-agent.exe"
mkdir -p "$(dirname "$OUT")"
cd "$ROOT/agent"
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -o "$OUT" ./cmd/wisla-arm-agent
echo "Built: $OUT"
ls -lh "$OUT"
