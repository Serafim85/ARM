# MVP smoke test — WISLA ARM

Пошаговая проверка сквозного сценария **DB → backend → ingest → DB**.

## Быстрый прогон (один скрипт)

```bash
chmod +x infra/smoke-stack.sh infra/run-agent-dev.sh
./infra/smoke-stack.sh
```

Скрипт:
1. Поднимает TimescaleDB (`infra/docker-compose.yml`)
2. Стартует backend (`wisla-arm` profile), если ещё не запущен
3. `POST /api/v1/agent/ingest` с тестовым batch
4. Проверяет `workstations`, `metric_values`, `arm_log_events`

**Требуется:** Docker Desktop запущен.

## Ручной прогон

```bash
# 1. DB
docker compose -f infra/docker-compose.yml up -d

# 2. Backend
./infra/run-backend-dev.sh

# 3. curl ingest (другой терминал)
curl -X POST http://localhost:8081/api/v1/agent/ingest \
  -H "Content-Type: application/json" \
  -H "X-Agent-Key: dev-arm-ingest-key" \
  -d @tests/fixtures/ingest-batch-linux.json

# 4. DB
docker exec wisla-arm-timescaledb psql -U wisla_arm -d wisla_arm \
  -c "SELECT id, hostname, status FROM workstations ORDER BY id DESC LIMIT 5;"
docker exec wisla-arm-timescaledb psql -U wisla_arm -d wisla_arm \
  -c "SELECT device_ip, metric_name, metric_value FROM metric_values ORDER BY recorded_at DESC LIMIT 5;"

# 5. Go agent (непрерывно)
./infra/run-agent-dev.sh
```

## Integration test

```bash
# backend + DB must be running
BACKEND_BASE_URL=http://localhost:8081 \
AGENT_INGEST_API_KEY=dev-arm-ingest-key \
  cd tests/integration && mvn test -Dtest=AgentIngestBatchIT,HealthCheckIT
```

## Шаблоны мониторинга

`templates/arm-linux.template`, `templates/arm-windows.template` — item keys совпадают с агентом:

- `arm.cpu.util` — CPU, %
- `arm.mem.used` — RAM, bytes
- `arm.disk.root.used_pct` — диск, %

Пересборка после правки YAML в `templates/src/`:

```bash
./templates/encode.sh
```

Проверка: `cd backend && mvn test -Dtest=ArmMonitoringTemplateTest`

## Следующий шаг MVP

→ UI workstations list (reuse monitoring → ARM). См. `docs/PHASES.md` MVP test gate.
