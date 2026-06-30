# Test coverage — WISLA АРМ

> Живая матрица: **требование → слой теста → файл/спека**.  
> Agents: добавляй строку при реализации фичи.  
> Фазы: `docs/PHASES.md` · Политика: `docs/testing.md`.

**Legend:** ⬜ planned · 🟡 partial · ✅ done · — not in phase

---

## Test pyramid

```text
                    ┌─────────────┐
                    │  E2E UI     │  tests/e2e/          (Playwright)
                    │  few, slow  │
              ┌─────┴─────────────┴─────┐
              │  Integration API+DB    │  tests/integration/  (JUnit+Testcontainers)
              │  medium                  │
        ┌─────┴──────────────────────────┴─────┐
        │  Unit (colocated)                     │  agent/*_test.go
        │  many, fast                           │  backend/src/test/
        └───────────────────────────────────────┘  frontend/*.spec.ts
```

| Project | Path | Stack | Runs against |
|---|---|---|---|
| Unit agent | `agent/` | Go testing | mocks |
| Unit backend | `backend/` | JUnit 5, Mockito | mocks |
| Unit frontend | `frontend/` | Jasmine/Karma | mocks |
| **Integration** | `tests/integration/` | JUnit 5, RestAssured, Testcontainers | real PG + HTTP API |
| **E2E** | `tests/e2e/` | Playwright | browser + full stack |
| Fixtures | `tests/fixtures/` | JSON, configs | shared |

---

## MVP coverage matrix

| ID / Scenario | Phase | Unit | Integration | E2E | Status | Test location |
|---|---|---|---|---|---|---|
| REQ-INFRA-DB TimescaleDB | MVP | — | ⬜ ingest writes metric_values | — | ⬜ | `DatabaseSchemaIT` |
| REQ-1.5 ingest API auth | MVP | ⬜ | 🟡 `AgentIngestControllerTest` | ⬜ | 🟡 partial | `AgentIngestControllerTest` |
| REQ-1.5 batch persist | MVP | ⬜ | 🟡 `AgentIngestBatchIT` | — | 🟡 partial | `tests/integration/` + `infra/smoke-stack.sh` |
| REQ-1.4 buffer offline | MVP | ⬜ | — | — | ⬜ | `agent/internal/buffer/*_test.go` |
| REQ-2.5 online/offline | MVP | ⬜ | ⬜ | ⬜ | ⬜ | `WorkstationStatusIT`, `workstations.spec.ts` |
| REQ-3.1 CPU/RAM/disk metrics | MVP | ⬜ | ⬜ | ⬜ | ⬜ | collector tests + `arm-card.spec.ts` |
| REQ-4.1 service status | MVP | ⬜ | ⬜ | — | ⬜ | collector + `ServiceStatusIT` |
| REQ-4.5 BSoD event | MVP | 🟡 | 🟡 | — | 🟡 | `WorkstationTelemetryIT`, ingest `events[]` |
| REQ-4.7 logs view | MVP | — | 🟡 | 🟡 | 🟡 | `GET .../logs`, карточка АРМ |
| REQ-7.* alert Email/TG | MVP | ⬜ | ⬜ | — | ⬜ | `AlertNotificationIT` (mock SMTP) |
| REQ-8 login UI | MVP | — | — | 🟡 | 🟡 | `tests/e2e/tests/smoke/login.spec.ts` |
| REQ-8 workstation list | MVP | — | 🟡 | 🟡 | 🟡 | `workstations-list.spec.ts`, `WorkstationListIT` |
| REQ-8 arm card graphs | MVP | — | 🟡 | 🟡 | 🟡 | `arm-card.spec.ts` |
| Smoke: stack up | MVP | — | 🟡 | 🟡 | 🟡 | `HealthCheckIT`, `smoke.spec.ts`, `infra/smoke-stack.sh` |

---

## v1 coverage matrix (planned)

| Scenario | Phase | Integration | E2E |
|---|---|---|---|
| Policy pull → agent config | v1 | ⬜ `AgentPolicyIT` | ⬜ `policy.spec.ts` |
| LDAP login | v1 | ⬜ `LdapAuthIT` | ⬜ `ldap-login.spec.ts` |
| Report CSV/XLSX export | v1 | ⬜ `ReportExportIT` | ⬜ `reports.spec.ts` |
| Session audit §5 | v1 | ⬜ `SessionAuditIT` | — |
| Retention job | v1 | ⬜ `RetentionPolicyIT` | — |

---

## E2E scenarios (MVP smoke suite)

| Spec | User story | Preconditions |
|---|---|---|
| `login.spec.ts` | Оператор входит локально | backend + frontend up, test user |
| `workstations-list.spec.ts` | После ingest АРМ в списке | fixture batch via API |
| `arm-card.spec.ts` | Карточка показывает CPU/RAM/диск | metrics in DB |
| `smoke.spec.ts` | Health + главная без 500 | docker-compose |

---

## Integration scenarios (MVP)

| Class | User story |
|---|---|
| `HealthCheckIT` | `GET /api/public/app-config` → 200 |
| `AgentIngestBatchIT` | POST batch → rows in `metric_values` + `workstations` |
| `AgentIngestAuthIT` | Invalid/missing API-key → 401 |
| `TemplateThresholdIT` | Metric over threshold → `monitoring_events` |
| `WorkstationStatusIT` | Stale heartbeat → offline |

---

## Agent unit (MVP)

| Package | Must test |
|---|---|
| `internal/collector/linux` | CPU, mem, disk parsers |
| `internal/collector/windows` | same |
| `internal/transport` | batch JSON shape, headers |
| `internal/buffer` | enqueue, flush, eviction |

---

## When to add tests

| Event | Action |
|---|---|
| New service method with logic | Unit in same module |
| New REST endpoint | Integration in `tests/integration/` |
| New UI flow (pilot-critical) | E2E spec in `tests/e2e/` |
| Bug fix | Regression + row in `docs/testing.md` |
| Phase gate | Update Status column in this file |

---

## Commands

```bash
./tests/run-all.sh --unit              # agent + backend + frontend unit
./tests/run-all.sh --integration       # tests/integration/
./tests/run-all.sh --e2e               # Playwright (stack must be up)
./tests/run-all.sh --all               # everything
```

See `tests/integration/README.md` and `tests/e2e/README.md`.
