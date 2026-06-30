#!/usr/bin/env bash
# Shared helpers for demo alert scripts.
demo_workstation_ip() {
  local hostname="${1:-}"
  case "$hostname" in
    pilot-linux-01) echo "${PILOT_LINUX_IP:-192.168.64.11}" ;;
    pilot-windows-01) echo "${PILOT_WINDOWS_IP:-192.168.64.12}" ;;
    *)
      if [[ -n "${WORKSTATION_IP:-}" ]]; then
        echo "$WORKSTATION_IP"
      else
        echo "192.168.64.11"
      fi
      ;;
  esac
}

demo_ingest_body() {
  local fixture="$1"
  local hostname="$2"
  local now="$3"
  local ip
  ip="$(demo_workstation_ip "$hostname")"
  sed \
    -e "s/pilot-linux-01/${hostname}/g" \
    -e "s/2026-06-24T12:00:00Z/${now}/g" \
    -e "s/192\\.168\\.1\\.10/${ip}/g" \
    "$fixture"
}
