# AGENTS.md — правила для AI agents

> **Читай этот файл первым** в каждой coding-сессии.  
> Живой статус: `docs/STATUS.md` · Решения: `docs/DECISIONS.md` · Roadmap: `docs/requirements/дорожная-карта.yaml`  
> Структура папок: `docs/REPO-LAYOUT.md`

---

## 1. Миссия проекта

**WISLA АРМ** — модуль мониторинга рабочих станций (Windows/Linux) для техподдержки.  
**Сейчас (MVP):** прототип на **2 VM** (Linux + Windows), сквозной сценарий «агент → сервер → UI → алерт».  
**Не сейчас:** интеграция WISLA (Kafka), SSO, S.M.A.R.T., инвентарь ПО, PDF-отчёты, горизонтальное масштабирование.

Продуктовый фильтр для любой задачи:

> Это нужно для **MVP demo** на 2 VM или закладки архитектуры под v1?  
> Да → делай. Нет → backlog в STATUS, не кодь.

---

## 2. Карта документов

| Файл | Зачем агенту |
|---|---|
| `docs/REPO-LAYOUT.md` | **Куда класть** код, docs, design, tests |
| `docs/requirements/требования-мониторинг-АРМ.docx` | Исходное ТЗ |
| `docs/requirements/проблемные-места-требований_V1.xlsx` | Ответы заказчика (90 вопросов) |
| `docs/requirements/дорожная-карта.yaml` / `.docx` | MVP → v1 → v2, **infrastructure** |
| `docs/requirements/трекинг-требований.yaml` / `.xlsx` | 55 требований, REQ-INFRA-DB |
| `docs/architecture/архитектура-и-техническая-реализация.docx` | Варианты A/D, контракт агента |
| `design/mockups/` | UI-референсы (login, карточка АРМ) |
| `vendor/NetworckScaner-master/` | **Референс** для форка — не править напрямую |
| `docs/STATUS.md` | **Что сделано / backlog** — обновляй каждую сессию |
| `docs/DECISIONS.md` | ADR |
| `docs/CODING-STANDARDS.md` | Стандарты Go / Java / Angular |
| `docs/PHASES.md` | **MVP / v1** — scope, DoD, test gates |
| `docs/TEST-COVERAGE.md` | Матрица требование → unit / integration / E2E |
| `docs/testing.md` | Политика тестирования, команды |
| `docs/ARCHITECTURE.md` | Модули, контракты |

---

## 3. Ритуал сессии

### Старт

1. Прочитать `docs/STATUS.md` — не дублировать сделанное.
2. Прочитать `docs/DECISIONS.md` — не пересматривать решённое без запроса.
3. Взять **одну** задачу из «In progress» или top of «Backlog».
4. Confirm scope fits **MVP** (`docs/PHASES.md`, `docs/requirements/дорожная-карта.yaml`).
5. Новые файлы — только в зону из `docs/REPO-LAYOUT.md`.
6. Новая фича → unit + строка в `docs/TEST-COVERAGE.md`; API/UI flow → integration/E2E.

### Во время работы

- Минимальный diff — не рефакторить unrelated code (особенно SNMP/scan в NS).
- Следовать `docs/ARCHITECTURE.md` и ADR; отклонения → `docs/DECISIONS.md`.
- **Go agent:** `go test ./...`, `gofmt`; unit in same session (`06`, `10-test-pyramid.mdc`).
- **Backend:** `./mvnw test` (module unit); cross-API → `tests/integration/`.
- **Frontend:** `ng test` (component unit); UI flows → `tests/e2e/`.
- Не коммитить secrets, `.env`, API-keys, credentials в git.
- Комментарии — только non-obvious business/security logic. **Code in English.**

### Конец (обязательно)

Обновить `docs/STATUS.md`:

- **Done** — что завершено (дата, файлы/модули)
- **In progress** — что осталось недоделанным
- **Backlog** — новые задачи, tech debt
- **Blockers** — что нужно от human
- **Session log** — 2–5 строк: что сделано, как проверить

Архитектурное решение → `docs/DECISIONS.md`.

---

## 4. Tech stack (MVP)

| Layer | Choice | Notes |
|---|---|---|
| Agent | **Go** | systemd + Windows Service; SQLite buffer; HTTPS batch JSON |
| Agent (future) | **C** (v1/v2 опционально) | Тот же JSON-контракт и item keys — см. ADR-003 |
| Backend | **Java 17 / Spring Boot 3.4** | Форк NetworckScaner; модули `workstation`, `agent-ingest` |
| Frontend | **Angular 21 + PrimeNG** | Форк NS; WorkstationsPage, ArmCard |
| Database | **TimescaleDB (PostgreSQL 16)** | БД `wisla_arm`, порт 5435; `metric_values` hypertable |
| Thresholds | **Zabbix `.template`** | `arm-linux.template`, `arm-windows.template` |
| Alerts | Email + Telegram | Модули notifications из NS |
| Dev | **docker-compose** | timescaledb + backend + frontend |

**Non-goals MVP:** WISLA Kafka, SSO, InfluxDB/MongoDB, безагентный режим, scan/SNMP/topology.

---

## 5. Repo layout (зоны)

Полное описание: **`docs/REPO-LAYOUT.md`**

```text
/
├── agent/          backend/       frontend/      templates/     # CODE
├── infra/                                                      # docker-compose, deploy
├── design/           mockups/ · samples/                        # DESIGN
├── docs/             requirements/ · architecture/ + *.md     # DOCS
├── tests/            e2e/ · integration/ · fixtures/           # cross-stack TESTS
├── vendor/           NetworckScaner-master/                    # reference only
├── AGENTS.md
└── README.md
```

Unit-тесты — **внутри** `agent/`, `backend/`, `frontend/`.  
E2E и fixtures — **`tests/`**.

**NS reuse:** monitoring (.template runtime), events, users, dashboards, notifications, Flyway/TimescaleDB.  
**NS remove/disable:** network.scan, scanjobs, topology, inventory/cisco.

---

## 6. Coding standards

**Полный документ:** `docs/CODING-STANDARDS.md`  
**Cursor rules:** `.cursor/rules/02–10`

Кратко:

- **Readable:** English in code; clear names; small functions; Javadoc/GoDoc on public API
- **Maintainable:** layer boundaries (ingest → domain → persistence); tests as spec
- **Scalable:** `AgentIngestPort`, `WislaIntegrationPort` stubs; trait/interface only when 2nd impl planned
- **Concise:** YAGNI, Rule of Three, no empty wrappers

---

## 7. MVP checklist (Definition of Done)

Полный scope, v1 и **test gates:** `docs/PHASES.md` · Матрица тестов: `docs/TEST-COVERAGE.md`.

- [ ] Форк NS → `backend/` + `frontend/` (без scan/SNMP)
- [ ] TimescaleDB в `infra/docker-compose`, Flyway, `wisla_arm`
- [ ] Go-агент: метрики CPU/RAM/диск, heartbeat, API-key, SQLite buffer
- [ ] Agent ingest API → `metric_values`, `workstations`
- [ ] `templates/arm-linux.template`, `templates/arm-windows.template`
- [ ] UI: список АРМ, карточка, графики
- [ ] Алерт Email/Telegram при пороге
- [ ] BSoD + warning/error логи в карточке
- [ ] 2 VM стабильно 3+ дня
- [ ] `./tests/run-all.sh --unit --integration` green; E2E smoke green

### Test projects (отдельно от unit)

| Project | Path |
|---|---|
| Integration API+DB | `tests/integration/` (Maven) |
| E2E UI+stack | `tests/e2e/` (Playwright) |
| Fixtures | `tests/fixtures/` |
| Runner | `./tests/run-all.sh` |

---

## 8. Коммуникация с human

**Спрашивай**, если:

- Scope выходит за MVP
- Breaking agent API или `.template` item keys
- Удаление/изменение контракта NS, от которого зависит WISLA v2
- Security-sensitive (auth, logging PII, API-key storage)
- Новая тяжёлая dependency

**Не спрашивай** для: bugfixes, tests, docs, internal refactor в рамках модуля, STATUS update.

---

## 9. Git / commits

- One logical change per commit when possible.
- Message format: `feat(scope): …` / `fix(scope): …` / `docs: …`
- Scopes: `agent`, `backend`, `frontend`, `templates`, `docs`, `infra`
- Don't commit without updating STATUS if feature-level work.
- Human decides when to push — agent prepares ready state.

---

## 10. Anti-patterns (запрещено)

- WISLA integration, SSO, S.M.A.R.T. в MVP
- Переписывание агента на C до стабильного Go-контракта
- Новый стек метрик (InfluxDB) вместо TimescaleDB
- «Красивые абстракции» без working vertical slice
- Skipping STATUS update at session end
- E2E/integration only in `frontend/` or `backend/src/test` for cross-stack flows
- MVP gate without `./tests/run-all.sh --integration`
- Копирование SNMP/scan/topology из NS «на всякий случай»

---

*Maintained by agents + human review · MVP focus only*
