#!/usr/bin/env bash
# Cross-compile wisla-arm-agent for Linux (amd64 + arm64).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/agent/dist"
mkdir -p "$DIST"
cd "$ROOT/agent"

echo "Building linux/amd64..."
GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -o "$DIST/wisla-arm-agent-linux-amd64" ./cmd/wisla-arm-agent

echo "Building linux/arm64..."
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o "$DIST/wisla-arm-agent-linux-arm64" ./cmd/wisla-arm-agent

# Default name: arm64 on Apple Silicon host, amd64 on Intel Mac
if [[ "$(uname -m)" == "arm64" ]]; then
  cp "$DIST/wisla-arm-agent-linux-arm64" "$DIST/wisla-arm-agent-linux"
  echo "Default wisla-arm-agent-linux -> arm64 (typical for Ubuntu VM on Apple Silicon)"
else
  cp "$DIST/wisla-arm-agent-linux-amd64" "$DIST/wisla-arm-agent-linux"
  echo "Default wisla-arm-agent-linux -> amd64"
fi

ls -lh "$DIST"/wisla-arm-agent-linux*
