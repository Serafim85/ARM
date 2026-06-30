# Wisla Contract Versioning Policy

Документ фиксирует lightweight правила эволюции интеграционного контракта NS -> Wisla (Story NS-3).

## Contract scope

- Bootstrap API NS-1: `GET /api/integration/wisla/v1/monitored-devices`.
- Event payload NS-2:
  - `ProbeAvailabilityUpdate`
  - `ExternalIncidentUpsert`
  - `MonitorStateSnapshot`

## Versioning rules

### Bootstrap API (REST)

- **Non-breaking:** добавление optional-полей в response, уточнение описаний.
- **Breaking:** удаление/переименование полей, изменение типа поля, изменение семантики query-параметров.
- Для breaking changes обязателен новый API major path (`/v2/...`) и параллельная поддержка предыдущей версии в agreed window.

### Event payload (Kafka)

- **Non-breaking:** добавление optional поля, добавление нового enum значения (с уведомлением Wisla).
- **Breaking:** удаление/переименование поля, изменение типа/required-статуса, изменение ключевой семантики поля.
- Для breaking changes создаётся новая версия схемы и новая линия topic naming (например `wisla.incidents.v2`) либо согласованная стратегия dual-publish.

## Deprecation policy

- Минимальный lead time для удаления deprecated поля/версии: **14 календарных дней** после уведомления Wisla.
- Уведомление отправляется ответственным NS-владельцем в agreed канал коммуникации команды Wisla.
- В период deprecation обязательно:
  - запись в `CONTRACT_CHANGELOG.md`;
  - migration note (что менять потребителю Wisla);
  - указание planned removal date.

## Mandatory change workflow

При любом изменении интеграционного контракта:

1. Обновить соответствующий артефакт в `docs/contracts/wisla/`.
2. Добавить запись в `CONTRACT_CHANGELOG.md`.
3. Если изменение breaking/deprecated — добавить migration note и planned date.
