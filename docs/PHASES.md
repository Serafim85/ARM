# Фазы разработки — MVP и v1

> **Для AI agents и разработчиков.** Канон scope: `docs/requirements/дорожная-карта.yaml`.  
> Требования по ID: `docs/requirements/трекинг-требований.yaml`.  
> Тесты: `docs/TEST-COVERAGE.md`.

**Текущая фаза:** MVP (prototype)  
**Следующая:** v1 (pilot) — только после закрытия MVP gate ниже.

---

## Сводка

| | MVP | v1 | v2 |
|---|---|---|---|
| **Codename** | prototype | pilot | production |
| **Аудитория** | Команда + демо заказчику | Инженеры ТП | Эксплуатация |
| **Масштаб** | 2 VM | 50–300 АРМ | до 5000 АРМ |
| **Срок (ориентир)** | 3–4 недели | 2–3 мес после MVP | 3–6 мес после v1 |
| **Покрытие ТЗ** | ~45% | ~85% | 100% (кроме waived) |
| **Зависит от** | — | MVP done | v1 done |

---

## MVP (prototype)

### Цель

Рабочий прототип на **2 VM (Linux + Windows)**: агент → ingest → PostgreSQL → UI → алерт.  
Задел архитектуры (`AgentIngestPort`, стабильный JSON-контракт) без v2-фич.

### Критерии успеха (Definition of Done)

- [ ] Агенты на 2 VM шлют метрики **стабильно 3+ дня**
- [ ] Список АРМ, карточка, графики CPU / RAM / диск
- [ ] Алерт Email или Telegram при остановке службы или нехватке диска
- [ ] BSoD фиксируется как событие
- [ ] Warning/error логи видны в карточке
- [ ] UI читаем на мобильном (список + карточка)
- [ ] **Test gate:** см. [MVP test gate](#mvp-test-gate)

### Deliverables (порядок работ)

| # | Deliverable | Папка | Требования |
|---|---|---|---|
| 1 | Форк NS без scan/SNMP/topology | `backend/`, `frontend/` | ADR-002 |
| 2 | docker-compose + TimescaleDB | `infra/` | REQ-INFRA-DB |
| 3 | Flyway: workstations, metric_values, events, logs | `backend/` | REQ-9.* |
| 4 | Go-агент (systemd + Windows Service) | `agent/` | REQ-1.4-* |
| 5 | Agent ingest API | `backend/` agent-ingest | REQ-1.5-* |
| 6 | `.template` linux/windows | `templates/` | REQ-7.* |
| 7 | UI: список + карточка + графики | `frontend/` | REQ-8.* |
| 8 | Email + Telegram alerts | `backend/` notifications | REQ-7.* |
| 9 | Пилот 2 VM, soak 3 дня | `infra/`, VMs | pilot в meta |

### In scope (разделы ТЗ)

| § | MVP |
|---|---|
| §1 | Агент, HTTPS+API-key, batch, буфер, авто-регистрация |
| §2 | Реестр, online/offline, одна группа |
| §3 | CPU, RAM, системный том, online/offline |
| §4 | Службы (конфиг), BSoD, логи warning/error (упрощённо) |
| §5 | Last seen, текущий пользователь (best effort) |
| §6 | — |
| §7 | `.template`, Email, Telegram, окно ТО (простое) |
| §8 | Список, карточка, графики, mobile, локальный вход |
| §9 | Метрики 30 д, логи/события 90 д |
| §10 | Базовый дашборд |

### Explicitly OUT (не делать в MVP)

- WISLA / Kafka integration
- SSO
- S.M.A.R.T., инвентарь ПО
- PDF-отчёты, Security log audit
- Политики агента с сервера
- Горизонтальное масштабирование (только hooks)

### MVP test gate

Перед переходом к v1 **обязательно зелёные**:

```bash
./tests/run-all.sh --unit --integration
./tests/run-all.sh --e2e --smoke
```

| Слой | Проект | MVP minimum |
|---|---|---|
| Unit | `agent/`, `backend/`, `frontend/` | collectors, ingest service, mappers, key UI utils |
| Integration | `tests/integration/` | ingest → DB; workstation registry; template trigger |
| E2E | `tests/e2e/` | login; АРМ в списке после ingest; карточка с метриками |
| Manual | 2 VM | soak 3 days (checklist в STATUS) |

---

## v1 (pilot)

### Цель

Пилот **50–300 АРМ**; закрыты основные требования ТЗ §1–§9 для инженеров техподдержки.

### Критерии успеха

- [ ] 50+ АРМ в production-пилоте
- [ ] Политики с сервера без переустановки агента
- [ ] Полный §4.7–4.8: логи, правила, «обработано»
- [ ] §5: вход/выход, сессии, RDP/SSH
- [ ] Отчёты CSV + XLSX с рекомендациями
- [ ] LDAP-вход
- [ ] Обновление агента RPM/deb/MSI
- [ ] **Test gate:** расширенное E2E + regression suite

### Deliverables v1

| Deliverable | Зона |
|---|---|
| Policy API + pull-config в агенте | `backend/`, `agent/` |
| Log pipeline: dedup, correlation_id | `backend/` |
| Reports module (CSV/XLSX) | `backend/`, `frontend/` |
| Agent update endpoint / packages | `backend/`, `infra/` |
| Retention policies в PostgreSQL | `backend/`, Flyway |
| LDAP auth | `backend/`, `frontend/` |

### v1 additions vs MVP

| § | v1 добавляет |
|---|---|
| §1 | §1.4.5–1.4.6 политики, лимиты, прокси UI, ротация key |
| §2 | Группы/теги, профили, инвентарь железа |
| §3 | Все тома, iowait, метрики по логам |
| §4 | Полные логи, правила, журнал аварийных |
| §5 | Полный аудит сессий |
| §6 | CSV/XLSX отчёты, рекомендации |
| §7 | Все типы алертов, получатели по группам |
| §8 | Полная карточка, роли, аудит админов |
| §9 | Настраиваемый retention, суточная агрегация |
| §10 | Поиск по пользователю, комментарии, шаблоны отчётов |

### v1 test gate

| Слой | Minimum |
|---|---|
| Unit | policy parser, report generator, session audit |
| Integration | policy pull; LDAP; report export; retention job |
| E2E | policy change → agent applies; export report; LDAP login |
| Load | backlog: 50 ARM simulated ingest (fixtures) |

---

## v2 (кратко — не текущий фокус)

Production: WISLA integration, 5000 АРМ, S.M.A.R.T., tiered storage, SSO.  
Detail: `docs/requirements/дорожная-карта.yaml` → `phases.v2`.

---

## Правило для agents

1. **Работаем только в текущей фазе** (сейчас MVP), если human не сказал иначе.
2. Фича из v1 в MVP → **отклонить**, записать в STATUS backlog.
3. Каждый deliverable MVP → unit tests + строка в `docs/TEST-COVERAGE.md`.
4. Сквозные сценарии → `tests/integration/` или `tests/e2e/`, не в `frontend/` root.
5. Закрытие фазы → обновить чеклисты здесь и MVP gate в STATUS.

---

## Связанные документы

- `AGENTS.md` — ритуал сессии
- `docs/requirements/дорожная-карта.yaml` — полная матрица покрытия ТЗ
- `docs/TEST-COVERAGE.md` — матрица требование → тест
- `docs/testing.md` — политика и команды
