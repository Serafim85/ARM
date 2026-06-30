#!/usr/bin/env bash
# Print agent env blocks for VM rehearsal (run on Mac).
set -euo pipefail
MAC_IP="${MAC_IP:-$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "CHANGE_ME")}"
LINUX_VM_IP="${1:-<LINUX_VM_IP>}"
WIN_VM_IP="${2:-<WINDOWS_VM_IP>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Mac host ==="
echo "  Backend URL for VMs: http://${MAC_IP}:8081"
echo "  UI (Mac only):       http://localhost:3000"
echo "  Agent binaries:      ${ROOT}/agent/dist/"
echo ""
echo "=== Linux VM (bash) ==="
cat <<EOF
export WISLA_ARM_AGENT_KEY=dev-arm-ingest-key
export WISLA_ARM_SERVER_URL=http://${MAC_IP}:8081
export WISLA_ARM_HOSTNAME=pilot-linux-01
export WISLA_ARM_OS_TYPE=linux
export WISLA_ARM_PRIMARY_IP=${LINUX_VM_IP}
export WISLA_ARM_POLL_INTERVAL_SEC=30
~/wisla-arm-agent-linux
EOF
echo ""
echo "=== Windows VM (PowerShell) ==="
cat <<EOF
\$env:WISLA_ARM_AGENT_KEY = "dev-arm-ingest-key"
\$env:WISLA_ARM_SERVER_URL = "http://${MAC_IP}:8081"
\$env:WISLA_ARM_HOSTNAME = "pilot-windows-01"
\$env:WISLA_ARM_OS_TYPE = "windows"
\$env:WISLA_ARM_PRIMARY_IP = "${WIN_VM_IP}"
\$env:WISLA_ARM_POLL_INTERVAL_SEC = "30"
.\\wisla-arm-agent.exe
EOF
echo ""
echo "=== Copy to Linux VM ==="
echo "  scp ${ROOT}/agent/dist/wisla-arm-agent-linux user@${LINUX_VM_IP}:~/"
