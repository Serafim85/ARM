# NS as Source of Truth: differences to fix in wiSLA

Этот документ фиксирует расхождения между:
- текущим контрактом/реализацией на стороне NS (source of truth),
- документом `contractRules.md` для wiSLA NS integration.

Цель: привести wiSLA к фактическому producer-контракту NS.

## Принятые фиксации (authoritative from NS)

- `sourceSystem`: берется из проперти; если не заполнено — fallback в имя хоста.
- `availability` enum: `AVAILABLE | NOT_AVAILABLE | UNDEFINED`.
- поле времени в availability payload: `checkedAt`.
- `externalDeviceId`: тип `Long` (numeric).
- поле статуса инцидента: `incidentStatus`.
- NS отправляет только `eventId` (без `externalEventId`).
- `MONITOR_ON`: payload с вложенным объектом `device`.
- timestamps в событиях NS-2: UTC instant, строка ISO-8601 с суффиксом `Z` (совместимо с `java.time.Instant.parse`).

## Mismatches requiring wiSLA changes

### 1) Availability payload: timestamp field name

- В `contractRules.md`: `availabilityCheckedAt`.
- В NS: `checkedAt`.

Что нужно в wiSLA:
- принимать и использовать `checkedAt` как основное поле контракта NS.

### 2) Availability payload: externalDeviceId type

- В `contractRules.md`: `externalDeviceId` описан как string.
- В NS: `externalDeviceId` отправляется как numeric `Long`.

Что нужно в wiSLA:
- парсить `externalDeviceId` как числовой идентификатор (`Long`), без требования string.

### 3) Availability enum values

- В `contractRules.md`: допускаются два набора (`UP/DOWN/UNKNOWN` и `AVAILABLE/NOT_AVAILABLE/UNDEFINED`).
- В NS: фактически используется только `AVAILABLE/NOT_AVAILABLE/UNDEFINED`.

Что нужно в wiSLA:
- гарантировать поддержку NS-набора `AVAILABLE/NOT_AVAILABLE/UNDEFINED` как обязательного.
- поддержка альтернативного набора может оставаться backward-compatible, но не должна быть единственным допустимым вариантом.

### 4) Incident status field name

- В `contractRules.md`: поле `status`.
- В NS: поле `incidentStatus`.

Что нужно в wiSLA:
- принимать `incidentStatus` как контрактное поле от NS.
- при необходимости оставить `status` как совместимость/alias, но не требовать его от NS.

### 5) Incident event identifier precedence

- В `contractRules.md`: логика `externalEventId` -> `eventId` -> correlation key.
- В NS: в payload отправляется только `eventId`.

Что нужно в wiSLA:
- в ingestion для NS-источника использовать `eventId` как входной идентификатор события без ожидания `externalEventId`.

### 6) Monitoring state payload shape

- В `contractRules.md`: могут фигурировать flattened root или legacy-поля (`action`, `templateId` в корне).
- В NS: только вложенный `device` с полем `state` (`MONITOR_ON` / `MONITOR_OFF`); для `MONITOR_ON` — `name`, `ipAddress`, `templateIds[]`, `defaultTemplateVersion`.

Что нужно в wiSLA:
- принимать `MonitorStateSnapshot` строго в форме nested `device` (см. `schemas/monitor-state-snapshot.schema.json`).

### 7) Monitoring state Kafka topic

- В `contractRules.md`: один логический поток monitoring-state.
- В NS: один топик `wisla.monitor-state` с payload `MonitorStateSnapshot` (per-device). Топик `wisla.monitoring-state` не используется.

Что нужно в wiSLA:
- подписаться только на `wisla.monitor-state` для lifecycle мониторинга устройства.

## Notes for wiSLA implementation

- Дедупликация выполняется на стороне wiSLA; уникальность `eventId` в NS не является жестким требованием.
- Для `OPEN` инцидента `breachStartedAt` должен быть not null.
- Для `RESOLVED` инцидента `normalizedAt` может быть null.
- Metadata endpoint NS гарантирует атомарно-консистентный ответ.
