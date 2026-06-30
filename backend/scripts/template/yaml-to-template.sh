#!/usr/bin/env bash
# Converts monitoring template YAML to .template (UTF-8 reverse + Base64).
# Same algorithm as MonitoringTemplateObfuscator in the backend.
set -euo pipefail

KEEP_YAML=0
TARGET=""

usage() {
  cat <<'EOF'
Usage: yaml-to-template.sh [-k] <file.yaml|directory>

  -k    Keep source .yaml/.yml after conversion (default: delete)

Examples:
  yaml-to-template.sh template_os_linux_snmp_snmp.yaml
  yaml-to-template.sh -k backend/src/main/resources/monitoring-templates/
EOF
}

while getopts ":kh" opt; do
  case "$opt" in
    k) KEEP_YAML=1 ;;
    h) usage; exit 0 ;;
    *) usage >&2; exit 1 ;;
  esac
done
shift $((OPTIND - 1))

if [[ $# -ne 1 ]]; then
  usage >&2
  exit 1
fi
TARGET="$1"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required for UTF-8 safe encode/decode." >&2
  exit 1
fi

encode_file() {
  local yaml_file="$1"
  local out_file
  if [[ "$yaml_file" == *.yaml ]]; then
    out_file="${yaml_file%.yaml}.template"
  elif [[ "$yaml_file" == *.yml ]]; then
    out_file="${yaml_file%.yml}.template"
  else
    out_file="${yaml_file}.template"
  fi

  python3 - "$yaml_file" "$out_file" <<'PY'
import base64
import sys
from pathlib import Path

src, dst = sys.argv[1], sys.argv[2]
plain = Path(src).read_text(encoding="utf-8")
encoded = base64.b64encode(plain[::-1].encode("utf-8")).decode("ascii")
Path(dst).write_text(encoded, encoding="ascii")
PY

  echo "OK: $yaml_file -> $out_file"
  if [[ "$KEEP_YAML" -eq 0 ]]; then
    rm -f -- "$yaml_file"
    echo "Removed: $yaml_file"
  fi
}

if [[ -f "$TARGET" ]]; then
  case "$TARGET" in
    *.yaml|*.yml) encode_file "$TARGET" ;;
    *) echo "Expected .yaml or .yml file: $TARGET" >&2; exit 1 ;;
  esac
  exit 0
fi

if [[ ! -d "$TARGET" ]]; then
  echo "Path not found: $TARGET" >&2
  exit 1
fi

count=0
while IFS= read -r -d '' f; do
  encode_file "$f"
  count=$((count + 1))
done < <(find "$TARGET" -type f \( -name '*.yaml' -o -name '*.yml' \) -print0)

if [[ "$count" -eq 0 ]]; then
  echo "No .yaml/.yml files under $TARGET" >&2
  exit 0
fi

echo "Done. Converted $count file(s)."
