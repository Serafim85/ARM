#!/usr/bin/env bash
# Scans Zabbix template YAML exports for template macros referenced in triggers
# but missing from macros/templates sections. Usage:
#   ./scan-template-macro-gaps.sh /path/to/zabbix/templates
set -euo pipefail

ROOT="${1:-}"
if [[ -z "$ROOT" || ! -d "$ROOT" ]]; then
  echo "Usage: $0 <zabbix-templates-root>" >&2
  exit 1
fi

python3 - "$ROOT" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
macro_ref = re.compile(r"\{\$[A-Za-z0-9_.]+(?::[^}]*)?\}")
macro_decl = re.compile(r'^\s*macro:\s*"(\{\$[^"]+\})"', re.M)
template_name = re.compile(r'^\s*template:\s*"?([^"\n]+)"?', re.M)

for path in sorted(root.rglob("template_*.yaml")):
    text = path.read_text(encoding="utf-8", errors="replace")
    name_match = template_name.search(text)
    name = name_match.group(1) if name_match else path.name
    declared = set(macro_decl.findall(text))
    refs = set(macro_ref.findall(text))
    missing = sorted(
        ref for ref in refs
        if ref not in declared
        and not any(ref.startswith(base.rstrip("}")) for base in declared if ":" not in base)
        and not any(
            ref == base or (":" in ref and ref.split(":", 1)[0] + "}" in declared)
            for base in declared
        )
    )
    contextual_missing = []
    for ref in refs:
        if ":" not in ref:
            continue
        base = ref.split(":", 1)[0] + "}"
        if base not in declared and ref not in declared:
            contextual_missing.append(ref)
    bases = {r.split(":", 1)[0] + "}" for r in refs if ":" in r}
    base_missing = sorted(b for b in bases if b not in declared)
    if base_missing or contextual_missing:
        print(f"{path}")
        print(f"  template: {name}")
        if base_missing:
            print("  missing base macros:")
            for macro in base_missing:
                print(f"    - {macro}")
        print()

PY
