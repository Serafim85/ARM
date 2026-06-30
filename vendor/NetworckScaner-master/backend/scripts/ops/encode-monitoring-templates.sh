#!/usr/bin/env bash
# Encodes monitoring-templates/**/*.yaml to .template (reverse UTF-8 + Base64).
# Prefer: backend/scripts/template/yaml-to-template.sh (no Maven/Java required).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMPLATE_SCRIPT="${SCRIPT_DIR}/../template/yaml-to-template.sh"
if [[ -x "$TEMPLATE_SCRIPT" ]] && command -v python3 >/dev/null 2>&1; then
  exec "$TEMPLATE_SCRIPT" "${SCRIPT_DIR}/../../src/main/resources/monitoring-templates"
fi
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "$ROOT"
mvn -q compile exec:java \
  -Dexec.mainClass=com.networkscanner.backend.monitoring.impl.MonitoringTemplateObfuscatorMain \
  -Dexec.args="src/main/resources/monitoring-templates"
