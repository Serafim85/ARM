# WISLA АРМ

Модуль мониторинга рабочих станций (Windows/Linux) для техподдержки.

**Фаза:** MVP (прототип на 2 VM) · **Стек:** Go agent · Spring Boot · Angular · TimescaleDB

## Быстрый старт для разработчиков

1. Прочитать [`AGENTS.md`](AGENTS.md) — правила и ритуал сессии
2. Статус работ: [`docs/STATUS.md`](docs/STATUS.md)
3. Структура папок: [`docs/REPO-LAYOUT.md`](docs/REPO-LAYOUT.md)

## Структура репозитория

| Папка | Назначение |
|---|---|
| [`agent/`](agent/) | Go-агент |
| [`backend/`](backend/) | Spring Boot API |
| [`frontend/`](frontend/) | Angular UI |
| [`templates/`](templates/) | Zabbix `.template` |
| [`infra/`](infra/) | docker-compose, деплой |
| [`design/`](design/) | Макеты и UI-референсы |
| [`docs/`](docs/) | Документация и ТЗ |
| [`tests/`](tests/) | E2E и integration |
| [`vendor/`](vendor/) | Референс NetworckScaner (форк NS) |

## Требования, фазы, тесты

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — архитектура MVP
- [`docs/architecture/ARCHITECTURE-DIAGRAM.md`](docs/architecture/ARCHITECTURE-DIAGRAM.md) — **блок-схемы компонентов и потоков**
- [`docs/PHASES.md`](docs/PHASES.md) — MVP / v1 scope, DoD, test gates
- [`docs/requirements/дорожная-карта.yaml`](docs/requirements/дорожная-карта.yaml)
- [`docs/requirements/трекинг-требований.yaml`](docs/requirements/трекинг-требований.yaml)
- [`docs/TEST-COVERAGE.md`](docs/TEST-COVERAGE.md) — матрица требование → тест
- [`tests/`](tests/) — integration (Maven) + E2E (Playwright)
