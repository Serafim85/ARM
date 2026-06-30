# Integration tests — WISLA АРМ

**Отдельный Maven-проект** для сквозных API + DB тестов (не unit-тесты backend).

## Stack

- JUnit 5
- RestAssured (HTTP)
- Testcontainers PostgreSQL (когда нужен isolated DB)

## Prerequisites

Backend должен быть запущен (docker-compose или local):

```bash
# from repo root, when infra exists:
docker compose -f infra/docker-compose.yml up -d
```

## Run

```bash
cd tests/integration
mvn test

# custom backend URL
BACKEND_BASE_URL=http://localhost:8080 mvn test

# force run even if health check fails (debug only)
INTEGRATION_FORCE=1 mvn test
```

## When to add tests here

| Scenario | Class naming |
|---|---|
| New REST API contract | `*IT.java` in this module |
| DB side effects after API | same + JDBC or Testcontainers |
| Backend-only logic | `backend/src/test/` instead |

## MVP classes (planned)

- `HealthCheckIT` — smoke ✅
- `AgentIngestBatchIT` — ingest → DB (disabled until API exists)
- `AgentIngestAuthIT` — 401 without key
- `TemplateThresholdIT` — alert on threshold

See `docs/TEST-COVERAGE.md`.
