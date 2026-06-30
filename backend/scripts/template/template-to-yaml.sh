#!/usr/bin/env bash
# Converts .template back to plain YAML (Base64 decode + reverse UTF-8).
set -euo pipefail

KEEP_TEMPLATE=0
TARGET=""

usage() {
  cat <<'EOF'
Usage: template-to-yaml.sh [-k] <file.template|directory>

  -k    Keep source .template after conversion (default: delete)

Examples:
  template-to-yaml.sh template_os_linux_snmp_snmp.template
  template-to-yaml.sh -k ./monitoring-templates/
EOF
}

while getopts ":kh" opt; do
  case "$opt" in
    k) KEEP_TEMPLATE=1 ;;
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

decode_file() {
  local template_file="$1"
  local out_file="${template_file%.template}.yaml"

  python3 - "$template_file" "$out_file" <<'PY'
import base64
import sys
from pathlib import Path

src, dst = sys.argv[1], sys.argv[2]
raw = Path(src).read_text(encoding="utf-8").strip()
if not raw:
    raise SystemExit("Повреждён файл шаблона: пустое содержимое")
try:
    reversed_text = base64.b64decode(raw).decode("utf-8")
except Exception as exc:
    raise SystemExit("Повреждён файл шаблона: неверный Base64") from exc
plain = reversed_text[::-1]
Path(dst).write_text(plain, encoding="utf-8", newline="\n")
PY

  echo "OK: $template_file -> $out_file"
  if [[ "$KEEP_TEMPLATE" -eq 0 ]]; then
    rm -f -- "$template_file"
    echo "Removed: $template_file"
  fi
}

if [[ -f "$TARGET" ]]; then
  case "$TARGET" in
    *.template) decode_file "$TARGET" ;;
    *) echo "Expected .template file: $TARGET" >&2; exit 1 ;;
  esac
  exit 0
fi

if [[ ! -d "$TARGET" ]]; then
  echo "Path not found: $TARGET" >&2
  exit 1
fi

count=0
while IFS= read -r -d '' f; do
  decode_file "$f"
  count=$((count + 1))
done < <(find "$TARGET" -type f -name '*.template' -print0)

if [[ "$count" -eq 0 ]]; then
  echo "No .template files under $TARGET" >&2
  exit 0
fi

echo "Done. Converted $count file(s)."
