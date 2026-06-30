# Coding Standards — WISLA АРМ

> **Для AI agents и human review.**  
> Цель: код **читаемый**, **поддерживаемый**, **масштабируемый**, **лаконичный**.  
> Связано: `AGENTS.md` · `docs/DECISIONS.md` · `.cursor/rules/`

---

## 1. Принципы (порядок приоритета)

1. **Понятность** — код читают чаще, чем пишут.
2. **Working vertical slice** — end-to-end раньше polish.
3. **Минимальный diff** — только задача, без drive-by refactor.
4. **YAGNI** — не строим v2 в MVP.
5. **Стабильный контракт агента** — JSON + item keys не ломать без ADR.

---

## 2. Общие правила

### Имена

| Область | Правило | Пример |
|---|---|---|
| Packages/modules | domain language | `workstation`, `agent-ingest` |
| Types | существительные | `WorkstationEntity`, `IngestBatch` |
| Functions/methods | глагол + объект | `persistMetrics`, `collectCpuUsage` |
| Booleans | `is`, `has`, `should` | `isOnline`, `hasAgentKey` |
| Item keys | Zabbix-style, stable | `arm.cpu.util`, `arm.mem.used` |

### Функции

- Одна ответственность; ~40–60 строк — мягкий лимит.
- Early return вместо глубокой вложенности.
- **Code and identifiers in English**; docs to humans may be Russian.

### Комментарии

- Только non-obvious: security, platform quirks (WMI, journald), retention policy.
- Javadoc (Java), GoDoc (Go), TSDoc (Angular) на **public** API.

---

## 3. Go (agent)

**Rule file:** `.cursor/rules/05-go-agent-standards.mdc`

- Layout: `cmd/agent`, `internal/collector`, `internal/transport`, `internal/buffer`, `internal/config`
- Errors: wrap with `%w`; no silent `log.Fatal` in collector loop
- Platform code isolated in `internal/platform/linux`, `internal/platform/windows`
- Config: file + env; no secrets in repo
- SQLite buffer: bounded size; FIFO eviction documented
- Tests: table-driven for parsers/mappers; mock HTTP for transport

```go
// ❌ Daemon path
resp, _ := client.Post(url, "application/json", body)

// ✅
resp, err := client.Post(url, "application/json", body)
if err != nil {
    return fmt.Errorf("post ingest: %w", err)
}
defer resp.Body.Close()
```

---

## 4. Java / Spring Boot (backend)

**Rule file:** `.cursor/rules/03-java-spring-standards.mdc`

### Слои (fork NS)

```text
web (controllers) → api (services) → repository (JPA) → model (entities)
agent-ingest / workstation — отдельные packages, не смешивать с network.scan
```

**Запрещено:**

- SNMP/scan dependencies в новых ARM-модулях
- Business logic в `@RestController` — только DTO mapping + validation
- `unwrap()` JPA без явной причины

### Persistence

- Flyway migrations in `db/migration/` — **единственный** способ менять схему
- Timescale hypertable creation in migration SQL (see NS patterns)
- Transactions на service layer

### API

- Agent ingest: stable path prefix; version when breaking (ADR + human)
- Validation: `@Valid` on request DTOs
- Errors: structured JSON, no stack traces to agent

---

## 5. Angular / PrimeNG (frontend)

**Rule file:** `.cursor/rules/04-angular-standards.mdc`

- Reuse NS patterns: services, PrimeNG tables/charts, route guards
- Smart/dumb split: container pages vs presentational components
- API calls through injectable services, not components
- Mobile: list + card layouts from MVP scope
- No inline styles unless matching existing NS convention

---

## 6. Тесты

**Policies:** `.cursor/rules/06-tests-with-features.mdc`, `07-regression-tests.mdc`  
**Detail:** `docs/testing.md`

| Layer | Unit | Integration |
|---|---|---|
| Go agent | collectors, JSON batch builder | HTTP ingest against Testcontainers or mock server |
| Backend | services, mappers | `@SpringBootTest` + Testcontainers PostgreSQL |
| Frontend | pipes, utils | defer component E2E until pilot paths defined |

---

## 7. Масштабируемость (структура, не speculation)

| Ситуация | Действие |
|---|---|
| Один ingest path (HTTP) | Concrete `AgentIngestService` |
| WISLA v2 | Interface `WislaIntegrationPort` + `NoOp` now |
| Go agent only | No trait/interface in Go until second collector backend |

**Ports to preserve:**

- `AgentIngestPort` — HTTP today, worker/Kafka later
- `WislaIntegrationPort` — no-op MVP, real impl v2

---

## 8. Concise (anti-bloat)

- Rule of Three before extracting helpers
- No empty wrapper services
- Delete dead NS modules instead of `#if false` style comments
- Prefer NS existing notification/event code over rewrite

---

## 9. Security baseline (MVP)

- API-key in header, not query string
- Hash API-keys at rest (follow NS if pattern exists)
- HTTPS for agent transport (self-signed OK in dev with documented flag)
- No PII in logs beyond what ТЗ requires
- `.env` / keys — gitignored

---

## 10. Checklist before finish

See `.cursor/rules/08-before-you-finish.mdc`.

- [ ] Scope = MVP
- [ ] Tests for new behavior
- [ ] Flyway if schema changed
- [ ] Agent item keys match `.template`
- [ ] `docs/STATUS.md` updated
- [ ] ADR if architecture/API decision changed
