#!/usr/bin/env bash
# Run test suites for WISLA ARM.
# Usage: ./tests/run-all.sh [--unit|--integration|--e2e|--smoke|--all]

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

RUN_UNIT=0
RUN_INTEGRATION=0
RUN_E2E=0
RUN_SMOKE=0

if [[ $# -eq 0 ]]; then
  echo "Usage: $0 [--unit|--integration|--e2e|--smoke|--all]"
  exit 1
fi

for arg in "$@"; do
  case "$arg" in
    --unit) RUN_UNIT=1 ;;
    --integration) RUN_INTEGRATION=1 ;;
    --e2e) RUN_E2E=1 ;;
    --smoke) RUN_SMOKE=1 ;;
    --all) RUN_UNIT=1; RUN_INTEGRATION=1; RUN_E2E=1 ;;
    *) echo "Unknown option: $arg"; exit 1 ;;
  esac
done

run_unit() {
  echo "=== Unit: agent ==="
  if [[ -f agent/go.mod ]]; then
    (cd agent && go test ./...)
  else
    echo "skip agent (no go.mod yet)"
  fi

  echo "=== Unit: backend ==="
  if [[ -f backend/pom.xml ]]; then
    (cd backend && mvn -q test)
  else
    echo "skip backend (not forked yet)"
  fi

  echo "=== Unit: frontend ==="
  if [[ -f frontend/package.json ]]; then
    (cd frontend && npm test -- --watch=false)
  else
    echo "skip frontend (not forked yet)"
  fi
}

run_integration() {
  echo "=== Integration: tests/integration ==="
  (cd tests/integration && mvn -q test)
}

run_e2e() {
  echo "=== E2E: tests/e2e ==="
  if [[ ! -d tests/e2e/node_modules ]]; then
    echo "Run: cd tests/e2e && npm ci && npm run install:browsers"
    exit 1
  fi
  (cd tests/e2e && npm test)
}

run_smoke() {
  echo "=== Smoke: unit (ARM subset) + integration ==="
  run_unit_subset
  run_integration
  echo "=== Smoke: E2E (skipped when stack is down) ==="
  if [[ ! -d tests/e2e/node_modules ]]; then
    echo "Run: cd tests/e2e && npm ci && npm run install:browsers"
    exit 1
  fi
  (cd tests/e2e && npm run test:smoke)
}

run_unit_subset() {
  echo "=== Unit subset: agent ==="
  if [[ -f agent/go.mod ]]; then
    (cd agent && go test ./...)
  fi
  echo "=== Unit subset: backend (ARM tests) ==="
  if [[ -f backend/pom.xml ]]; then
    (cd backend && mvn -q test -Dtest=ArmWorkstationTemplateSupportTest,ArmMonitoringTemplateTest,AgentIngestServiceImplTest,WorkstationStatusSupportTest)
  fi
}

[[ $RUN_UNIT -eq 1 ]] && run_unit
[[ $RUN_INTEGRATION -eq 1 ]] && run_integration
[[ $RUN_E2E -eq 1 ]] && run_e2e
[[ $RUN_SMOKE -eq 1 ]] && run_smoke

echo "=== Done ==="
