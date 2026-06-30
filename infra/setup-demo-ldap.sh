#!/usr/bin/env bash
# Поднимает тестовый OpenLDAP и включает LDAP в directory_settings.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml" -f "$ROOT/infra/docker-compose.ldap.yml")

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker не запущен. Откройте Docker Desktop."
  exit 1
fi

echo "==> Starting TimescaleDB + LDAP..."
"${COMPOSE[@]}" up -d timescaledb ldap

echo "==> Waiting for PostgreSQL..."
for _ in $(seq 1 30); do
  if docker exec wisla-arm-timescaledb pg_isready -U wisla_arm -d wisla_arm >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "==> Waiting for LDAP (port 389)..."
for _ in $(seq 1 45); do
  if (echo >/dev/tcp/127.0.0.1/389) >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

if ! (echo >/dev/tcp/127.0.0.1/389) >/dev/null 2>&1; then
  echo "ERROR: LDAP not listening on localhost:389 — check: docker logs wisla-arm-ldap"
  exit 1
fi

echo "==> Enabling LDAP in directory_settings..."
docker exec -i wisla-arm-timescaledb psql -U wisla_arm -d wisla_arm <<'SQL'
UPDATE directory_settings SET
  enabled = TRUE,
  directory_type = 'LDAP',
  protocol = 'LDAP',
  server_host = 'localhost',
  server_port = 389,
  base_dn = 'dc=networkscanner,dc=local',
  auth_type = 'SIMPLE',
  bind_dn = 'cn=admin,dc=networkscanner,dc=local',
  bind_password = 'admin',
  user_filter = '(&(objectClass=inetOrgPerson)(uid={login}))',
  login_attribute = 'uid',
  email_attribute = 'mail',
  display_name_attribute = 'displayName',
  allow_local_fallback = TRUE,
  updated_at = NOW()
WHERE id = 1;
SQL

cat <<'EOF'

OK  Demo LDAP ready.

Login form → режим LDAP:
  admin     / password
  operator  / operator123
  viewer    / viewer123

Перезапустите backend, если он уже был запущен (читает настройки из БД при каждом входе).

EOF
