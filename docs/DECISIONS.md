# DECISIONS — Architecture Decision Log

> Формат ADR: **Status** · **Context** · **Decision** · **Consequences**  
> Agents: append new entries; don't delete — mark **Superseded** instead.

---

## ADR-001 — MVP scope (prototype)

**Status:** Accepted (2026-06-17)

**Context:** ТЗ §1–§10; согласования в Excel V1; команда 2 человека, ~3–4 недели.

**Decision:** MVP = ~45% ТЗ: агент Win/Linux, ingest, список+карточка, базовые метрики, алерты Email/Telegram, BSoD, логи warning/error, `.template` пороги, mobile layout, локальный вход.

**Consequences:** WISLA, SSO, S.M.A.R.T., инвентарь ПО, PDF — deferred. См. `docs/requirements/дорожная-карта.yaml` → `phases.mvp.explicitly_out`.

---

## ADR-002 — Base: fork NetworckScaner (Variant A → D)

**Status:** Accepted (2026-06-17)

**Context:** NS уже содержит UI, .template runtime, events, Kafka-мост, LDAP, SMTP, TimescaleDB.

**Decision:** Форк NS монолита; новые модули `workstation`, `agent-ingest`. Ingest спроектировать как `AgentIngestPort` для extract в v1/v2.

**Consequences:** Переиспользуем максимум NS; удаляем/отключаем scan, SNMP, topology, Cisco inventory.

---

## ADR-003 — Agent language: Go (MVP), C optional (v1/v2)

**Status:** Accepted (2026-06-17)

**Context:** Appendix A architecture doc; команда без сильного C-опыта; нужен быстрый пилот на 2 VM.

**Decision:** MVP-агент на **Go**. C — опциональная замена в v1/v2 при сохранении JSON schema и `.template` item keys.

**Consequences:** Контракт ingest стабилен; backend/UI не зависят от языка агента. Миграция Go→C = drop-in binary, не переписывание платформы.

---

## ADR-004 — Database: TimescaleDB (PostgreSQL 16)

**Status:** Accepted (2026-06-22)

**Context:** NS уже на TimescaleDB; §9 ТЗ — метрики + события; 5000 АРМ target.

**Decision:** Единая СУБД **TimescaleDB / PostgreSQL 16**. Dev: `timescale/timescaledb:latest-pg16`, порт host **5435**, БД **`wisla_arm`**. Метрики в hypertable `metric_values`.

**Consequences:** Не InfluxDB/MongoDB. SQLite только локальный буфер агента. REQ-INFRA-DB.

---

## ADR-005 — Agent transport: HTTPS batch JSON + API-key

**Status:** Accepted (2026-06-17)

**Context:** Excel V1: API-key, batch upload, auto registration; §1.5 ТЗ.

**Decision:** `POST` batch JSON; header `X-Agent-Key`; heartbeat в batch; авто-регистрация при первом успешном ingest.

**Consequences:** Kafka на агенте — не используем. Kafka для WISLA — только v2 server-side.

---

## ADR-006 — Thresholds: Zabbix `.template` files

**Status:** Accepted (2026-06-17)

**Context:** Заказчик ориентируется на Zabbix; NS имеет .template runtime.

**Decision:** `arm-linux.template`, `arm-windows.template`; item keys агента совпадают с template items.

**Consequences:** Не invent custom threshold DSL in MVP.

---

## ADR-007 — WISLA integration deferred to v2

**Status:** Accepted (2026-06-17)

**Context:** Excel: интеграция не обязательна в v1; Swagger от команды WISLA ещё не получен.

**Decision:** MVP/v1 без WISLA. Stub `WislaIntegrationPort` + `NoOpWislaIntegration`. Референс: NS `INTEGRATION_WISLA_*.md`.

**Consequences:** Не блокируем MVP на внешнем API.

---

## ADR-008 — Agent rules and session workflow

**Status:** Accepted (2026-06-22)

**Context:** Команда использует AI agents; в AI-Platform-Vision отработан `AGENTS.md` + `.cursor/rules/`.

**Decision:** Adopt `AGENTS.md`, `docs/STATUS.md`, `docs/DECISIONS.md`, `docs/CODING-STANDARDS.md`, `.cursor/rules/` for WISLA ARM.

**Consequences:** Agents update STATUS every session; ADR for architecture changes.

---

## ADR-009 — Repo layout: code / docs / design / tests / vendor

**Status:** Accepted (2026-06-22)

**Context:** В корне смешаны ТЗ, docx, фото, yaml и референс NS; нужно явное разделение для команды и agents.

**Decision:** Пять зон верхнего уровня:

- **CODE:** `agent/`, `backend/`, `frontend/`, `templates/`, `infra/`
- **DOCS:** `docs/` (+ `requirements/`, `architecture/` для артефактов ТЗ)
- **DESIGN:** `design/mockups/`, `design/samples/`
- **TESTS:** `tests/e2e/`, `integration/`, `fixtures/` (unit — colocated в CODE)
- **VENDOR:** `vendor/NetworckScaner-master/` — референс, без правок фич ARM

**Consequences:** Канон в `docs/REPO-LAYOUT.md`; rule `.cursor/rules/09-repo-layout.mdc`. Существующие файлы перенесены в новые пути.

---

## ADR-010 — Test pyramid: unit + separate integration and E2E projects

**Status:** Accepted (2026-06-22)

**Context:** Нужно покрытие не только unit, но и сквозных сценариев agent → API → DB → UI.

**Decision:**

- **Unit** — colocated в `agent/`, `backend/`, `frontend/`
- **Integration** — отдельный Maven-модуль `tests/integration/` (RestAssured, Testcontainers)
- **E2E** — отдельный Playwright-проект `tests/e2e/`
- **Fixtures** — `tests/fixtures/`; runner `./tests/run-all.sh`
- **MVP gate** — unit + integration green; E2E smoke green (`docs/PHASES.md`)

**Consequences:** `docs/TEST-COVERAGE.md`, `docs/testing.md`, rule `10-test-pyramid.mdc`. Cross-stack tests не в корне frontend/backend.

---

## ADR-011 — Agent ingest API path and MVP auth

**Status:** Accepted (2026-06-23)

**Context:** Need stable agent contract for Go agent and future C replacement.

**Decision:**

- `POST /api/v1/agent/ingest` with header `X-Agent-Key`
- MVP auth: shared key `app.agent.ingest-api-key` (env `AGENT_INGEST_API_KEY`)
- Auto-register `workstations` on first ingest by hostname
- Metrics → existing `metric_values`; logs → `arm_log_events`
- `AgentIngestPort` interface for future extract

**Consequences:** Packages `workstation`, `agentingest`; Flyway V66.

---

*Append new ADRs below as decisions are made during implementation.*
