#!/usr/bin/env bash
# Smoke test for workstation park export (CSV + XLSX).
set -euo pipefail
BACKEND="${BACKEND_BASE_URL:-http://localhost:8081}"
EMAIL="${DEMO_ADMIN_EMAIL:-admin@example.com}"
PASS="${DEMO_ADMIN_PASSWORD:-password}"
OUT_DIR="${1:-/tmp/arm-export-test}"

mkdir -p "$OUT_DIR"
TOKEN="$(curl -sf -X POST "${BACKEND}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASS}\",\"authMode\":\"LOCAL\"}" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")"

curl -sf -H "Authorization: Bearer ${TOKEN}" \
  "${BACKEND}/api/v1/workstations/export.csv?q=pilot" \
  -o "${OUT_DIR}/workstations.csv"
echo "OK  CSV  $(wc -c < "${OUT_DIR}/workstations.csv") bytes -> ${OUT_DIR}/workstations.csv"

curl -sf -H "Authorization: Bearer ${TOKEN}" \
  "${BACKEND}/api/v1/workstations/export.xlsx?q=pilot" \
  -o "${OUT_DIR}/arm-park-report.xlsx"
echo "OK  XLSX $(wc -c < "${OUT_DIR}/arm-park-report.xlsx") bytes -> ${OUT_DIR}/arm-park-report.xlsx"
echo "Open: open ${OUT_DIR}/arm-park-report.xlsx"
