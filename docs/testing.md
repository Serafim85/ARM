# Testing policy — WISLA АРМ

> **Три уровня:** unit (в модулях) · integration (`tests/integration/`) · E2E (`tests/e2e/`).  
> Матрица покрытия: `docs/TEST-COVERAGE.md` · Фазы: `docs/PHASES.md`.  
> Cursor rules: `06-tests-with-features.mdc`, `07-regression-tests.mdc`, `10-test-pyramid.mdc`.

---

## Принцип

| Уровень | Где | Когда | Скорость |
|---|---|---|---|
| **Unit** | `agent/`, `backend/`, `frontend/` | Каждая новая логика — **в той же сессии** | секунды |
| **Integration** | `tests/integration/` | Новый/изменённый API, DB, pipeline | минуты |
| **E2E** | `tests/e2e/` | Сквозные user flows (login, list, card) | минуты |

Unit alone **недостаточен** для ingest и UI — нужны отдельные проекты integration и E2E.

---

## Unit tests (colocated)

| Change | Where |
|---|---|
| Go collector/parser/batch | `agent/**/*_test.go` |
| Spring service/mapper | `backend/src/test/java/` |
| Angular pipes/utils | `frontend/**/*.spec.ts` |

**Same session** — не откладывать.

---

## Integration project (`tests/integration/`)

**Отдельный Maven-модуль** — не смешивать с `backend/src/test` для сквозных сценариев.

- JUnit 5 + RestAssured + Testcontainers (PostgreSQL/TimescaleDB)
- Поднимает или подключается к backend (`BASE_URL`, `JDBC`)
- Тестирует: ingest API, DB state, alerts, template thresholds

```bash
cd tests/integration && mvn test
# или: ./tests/run-all.sh --integration
```

Backend `@SpringBootTest` unit/integration — только для **изolated** module tests.  
Cross-module «agent POST → DB row» — **только** `tests/integration/`.

---

## E2E project (`tests/e2e/`)

**Отдельный Node/Playwright проект** — UI + optional API setup.

- Playwright against `frontend` URL
- Preconditions via API (fixture batch from `tests/fixtures/`)
- MVP smoke: login, workstation list, arm card

```bash
cd tests/e2e && npm ci && npx playwright test
# stack must be running — see tests/e2e/README.md
```

---

## Regression tests on bug fixes

1. Fix + test in same session.
2. Register in table below.
3. Prefer integration/E2E if bug was cross-layer.

| ID | Date | Area | Test location | Description |
|---|---|---|---|---|
| — | — | — | — | — |

---

## Phase gates

| Phase | Required before done |
|---|---|
| **MVP** | `./tests/run-all.sh --unit --integration` green; E2E smoke green; 2 VM soak manual |
| **v1** | + policy, LDAP, reports integration/E2E per TEST-COVERAGE |

---

## Fixtures

Shared data: `tests/fixtures/`

- `ingest-batch-linux.json` — sample agent payload
- `ingest-batch-windows.json`
- `test-agent.env.example` — API key for integration

---

## CI (backlog)

```yaml
# proposed pipeline
- unit: agent + backend + frontend
- integration: Testcontainers + backend jar
- e2e: docker-compose up + playwright (on main / nightly)
```

---

## Commands summary

```bash
./tests/run-all.sh --unit
./tests/run-all.sh --integration
./tests/run-all.sh --e2e
./tests/run-all.sh --all
```

Individual:

```bash
cd agent && go test ./...
cd backend && ./mvnw test
cd frontend && npm test -- --watch=false
cd tests/integration && mvn test
cd tests/e2e && npx playwright test
```

---

## Skip tests only for

- Typos, comments, docs-only, STATUS log
- Pure renames without behavior change
