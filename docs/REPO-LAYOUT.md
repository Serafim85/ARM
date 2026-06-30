# Структура репозитория WISLA АРМ

> **Канон.** Новые файлы кладём только в нужную зону.  
> ADR: `docs/DECISIONS.md` → ADR-009.

---

## Дерево (верхний уровень)

```text
/
├── AGENTS.md                 # Точка входа для AI agents (корень — намеренно)
├── README.md                 # Краткий обзор проекта
│
├── agent/                    # CODE — Go-агент
├── backend/                  # CODE — Spring Boot (форк NS)
├── frontend/                 # CODE — Angular + PrimeNG (форк NS)
├── templates/                # CODE — Zabbix .template для АРМ
├── infra/                    # INFRA — docker-compose, deploy-скрипты, CI
│
├── design/                   # DESIGN — макеты, референсы UI, образцы отчётов
├── docs/                     # DOCS — текстовая документация и артефакты ТЗ
├── tests/                    # TESTS — сквозные тесты (не unit внутри модулей)
└── vendor/                   # VENDOR — чужой код, только референс до форка
```

---

## CODE — исполняемый код и конфиги runtime

| Папка | Содержимое | Unit-тесты |
|---|---|---|
| `agent/` | Go: collector, transport, buffer, platform linux/windows | **рядом** в `*_test.go` |
| `backend/` | Java Spring Boot, Flyway, JPA | **рядом** в `src/test/java/` |
| `frontend/` | Angular компоненты, сервисы, стили | **рядом** в `*.spec.ts` |
| `templates/` | `arm-linux.template`, `arm-windows.template` | валидация в backend-тестах |
| `infra/` | `docker-compose.yml`, k8s/helm (v1+), CI configs | smoke в `tests/integration/` |

**Правило:** прикладной код **не** лежит в `docs/`, `design/`, `tests/` (кроме тестового кода в `tests/`).

---

## DOCS — документы

```text
docs/
├── STATUS.md              # Живой статус (agents обновляют каждую сессию)
├── DECISIONS.md           # ADR
├── ARCHITECTURE.md        # Архитектура и контракты
├── CODING-STANDARDS.md    # Стандарты кода
├── testing.md             # Политика тестирования
├── REPO-LAYOUT.md         # Этот файл
│
├── requirements/          # ТЗ, roadmap, трекинг, ответы заказчика
│   ├── требования-мониторинг-АРМ.docx
│   ├── дорожная-карта.yaml / .docx
│   ├── трекинг-требований.yaml / .xlsx
│   └── проблемные-места-требований_*
│
└── architecture/          # Статические архитектурные документы (docx)
    ├── архитектура-и-техническая-реализация.docx
    └── архитектурные-варианты.docx
```

| Тип | Куда | Пример |
|---|---|---|
| Живая документация агентов | `docs/*.md` | STATUS, DECISIONS |
| ТЗ и согласования | `docs/requirements/` | yaml, xlsx, docx от заказчика |
| Архитектура (exports) | `docs/architecture/` | docx для review |
| Cursor rules | `.cursor/rules/` | `.mdc` |

---

## DESIGN — дизайн и визуальные референсы

```text
design/
├── mockups/               # Скриншоты UI, wireframes, фото экранов
│   ├── photo_2026-05-19_18-00-02.jpg   # login
│   └── photo_2026-06-16_13-46-21.jpg   # карточка устройства
└── samples/               # Образцы отчётов заказчика (PDF и т.п.)
    ├── kolicestvo-bsod-sinii-ekran-smerti.pdf
    └── monitoring-aktivnyx-polzovatelei.pdf
```

**Правило:** картинки и PDF для UI/UX — только `design/`. Не дублировать в `docs/requirements/`.

---

## TESTS — сквозные проекты (не unit)

```text
tests/
├── integration/           # Maven: API + DB (RestAssured, Testcontainers)
├── e2e/                   # Playwright: UI + full stack
├── fixtures/              # ingest-batch-*.json
└── run-all.sh             # --unit | --integration | --e2e | --all
```

| Тип теста | Где |
|---|---|
| Unit (Go/Java/TS) | **внутри** `agent/`, `backend/`, `frontend/` |
| Regression registry | `docs/testing.md` |
| E2E / integration stack | `tests/e2e/`, `tests/integration/` |
| Test data | `tests/fixtures/` |

---

## VENDOR — референсный код (не наш продукт)

```text
vendor/
├── NetworckScaner-master/     # WISLA NS — источник форка
└── NetworckScaner-master.zip  # архив (опционально, можно не коммитить)
```

**Правило:** не правим `vendor/` для фич WISLA АРМ. Форк → копия в `backend/` + `frontend/`.  
NS reuse: monitoring, events, notifications, TimescaleDB patterns.

---

## INFRA — окружение и деплой

```text
infra/
├── docker-compose.yml         # timescaledb + backend + frontend (MVP)
├── docker-compose.dev.yml     # overrides для Mac
└── scripts/                   # bootstrap, migrate, package agent (по мере появления)
```

---

## Что куда **не** класть

| ❌ | ✅ |
|---|---|
| `.java` в `docs/` | `backend/src/main/java/` |
| Скриншот UI в корень | `design/mockups/` |
| E2E Playwright в `frontend/` | `tests/e2e/` |
| Правки агента в `vendor/` | `agent/` |
| ТЗ xlsx в `docs/` root | `docs/requirements/` |
| Secrets, `.env` | gitignore; только `.env.example` в `infra/` |

---

## Связанные файлы

- `AGENTS.md` §5 — краткая схема для agents
- `.cursor/rules/09-repo-layout.mdc` — напоминание в IDE
- `docs/DECISIONS.md` → ADR-009
