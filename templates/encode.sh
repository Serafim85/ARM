#!/usr/bin/env bash
# Rebuild .template files from YAML sources in templates/src/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENCODER="$ROOT/vendor/NetworckScaner-master/backend/scripts/template/yaml-to-template.sh"
SRC="$ROOT/templates/src"
OUT="$ROOT/templates"

if [[ ! -x "$ENCODER" ]]; then
  echo "Encoder not found: $ENCODER" >&2
  exit 1
fi

for yaml in "$SRC"/arm-linux.yaml "$SRC"/arm-windows.yaml; do
  "$ENCODER" -k "$yaml"
  mv -f "${yaml%.yaml}.template" "$OUT/"
done

python3 - "$SRC/manifest.yaml" "$OUT/manifest.template" <<'PY'
import base64
import sys
from pathlib import Path

src, dst = sys.argv[1], sys.argv[2]
plain = Path(src).read_text(encoding="utf-8")
encoded = base64.b64encode(plain[::-1].encode("utf-8")).decode("ascii")
Path(dst).write_text(encoded, encoding="ascii")
print(f"OK: {src} -> {dst}")
PY

echo "Done. Output: $OUT/*.template"
