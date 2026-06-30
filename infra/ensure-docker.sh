#!/usr/bin/env bash
# Verify Docker daemon is running; optionally start TimescaleDB compose service.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml")

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker не запущен."
  echo ""
  echo "Сделайте одно из:"
  echo "  1. Откройте Docker Desktop (Applications → Docker) и дождитесь «Engine running»"
  echo "  2. Или в терминале: open -a Docker"
  echo ""
  echo "Затем снова:"
  echo "  docker compose -f infra/docker-compose.yml up -d"
  exit 1
fi

if [[ "${1:-}" == "--up" ]]; then
  echo "Starting TimescaleDB (docker compose)..."
  "${COMPOSE[@]}" up -d
  echo "Waiting for PostgreSQL..."
  for _ in $(seq 1 30); do
    if docker exec wisla-arm-timescaledb pg_isready -U wisla_arm -d wisla_arm >/dev/null 2>&1; then
      echo "OK  PostgreSQL ready (wisla_arm@localhost:5435)"
      exit 0
    fi
    sleep 2
  done
  echo "ERROR: PostgreSQL not ready — check: docker compose -f infra/docker-compose.yml ps"
  exit 1
fi
