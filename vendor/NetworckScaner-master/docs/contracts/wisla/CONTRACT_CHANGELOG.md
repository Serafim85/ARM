# Contract Changelog (NS -> Wisla)

Формат записи:

- `version` — версия contract bundle.
- `date` — дата изменения.
- `type` — `added`, `changed`, `deprecated`, `removed`.
- `impact` — `non-breaking` или `breaking`.
- `details` — краткое описание.
- `migration` — что нужно сделать Wisla (если применимо).

---

## v1.0.0 — 2026-04-30

- type: `added`
- impact: `non-breaking`
- details:
  - Добавлен machine-readable OpenAPI snapshot bootstrap API: `openapi-wisla-bootstrap.json`.
  - Добавлены JSON Schema для событий:
    - `probe-availability-update.schema.json`
    - `external-incident-upsert.schema.json`
    - `monitoring-state-changed.schema.json`
  - Добавлена policy эволюции контракта: `CONTRACT_VERSIONING.md`.
- migration:
  - Для Wisla migration не требуется. Это baseline формализация NS-3.

## v1.1.0 — 2026-04-30

- type: `added`
- impact: `non-breaking`
- details:
  - Добавлен NS-4 runbook: `IDENTIFIERS_AND_CORRELATION_RUNBOOK.md`.
  - Зафиксированы lifecycle правила `sourceSystem + ns_device_id`.
  - Зафиксирован составной incident `correlationKey` и рекомендации для idempotency.
  - Зафиксированы правила согласованности `templateId/templateVersion` между bootstrap и incident payload.
- migration:
  - Для Wisla migration не требуется. Изменение документирует текущую реализацию NS.

## v1.2.0 — 2026-04-30

- type: `added`
- impact: `non-breaking`
- details:
  - Добавлен NS-5 runbook: `SECURITY_QUOTAS_LATENCY_RUNBOOK.md`.
  - Зафиксированы credentials model, quota/backoff guidance, latency ориентиры и replay/backfill policy.
- migration:
  - Для Wisla migration не требуется. Рекомендовано применить operational checklist из runbook.

## v1.3.0 — 2026-05-07

- type: `added`
- impact: `non-breaking`
- details:
  - Добавлен отдельный Kafka topic для per-device snapshot событий мониторинга: `wisla.monitor-state`.
  - Для `wisla.monitor-state` зафиксированы параметры retention: `retention.bytes=1073741824` (1GB), `retention.ms=1209600000` (14 дней), значения вынесены в NS properties.
  - Добавлен metadata endpoint: `GET /api/wisla/kafka-broker-metadata` (конфигурируемый path), который возвращает `bootstrapServers`, `schemaVersion`, `topics` и whitelisted `security`.
  - Добавлен JSON Schema: `schemas/monitor-state-snapshot.schema.json`.
  - Добавлен fallback для `sourceSystem`: property -> hostname -> `networkscanner`.
- migration:
  - Для Wisla рекомендовано подключить обработку `wisla.monitor-state` как per-device snapshot stream.
  - Для bootstrap metadata использовать endpoint `GET /api/wisla/kafka-broker-metadata` с Bearer token пользователя роли `WISLA_INTEGRATION` (или `ADMIN`).

## v2.1.0 — 2026-05-27

- type: `added`
- impact: `non-breaking` (для consumers, поддерживающих новый enum)
- details:
  - `device.state` расширен значением `DELETED` для физического удаления устройства из NS (`deactivate` / `deactivateByIds`).
  - `MONITOR_OFF` остаётся только для снятия всех шаблонов (запись в `monitored_device` сохраняется).
  - `schemaVersion` snapshot: `1.1` (принимается также `1.0` в JSON Schema).
- migration:
  - Wisla: `MONITOR_OFF` → `NOT_IN_USAGE`; `DELETED` → archive probe/services + delete trouble tickets.
  - Re-sync: устройства, отсутствующие в bootstrap, обрабатывать как удалённые.

## v2.0.0 — 2026-05-07

- type: `changed`
- impact: `breaking`
- details:
  - Удалён flat per-template поток `MonitoringStateChanged` и Kafka topic `wisla.monitoring-state` (и `.DLT`).
  - Единственный поток состояния мониторинга устройства: `MonitorStateSnapshot` в топике `wisla.monitor-state`; `device.state` = `MONITOR_ON` | `MONITOR_OFF` (с v2.1.0 также `DELETED`), для `MONITOR_ON` в `device` передаются `name`, `ipAddress`, `templateIds` (массив строк `id` или `id:version`), `defaultTemplateVersion`.
  - Удалены из payload snapshot: `ownerId`, `occurredAt`, root-level `state`, `templateIdsRaw`, `monitored`.
  - Временные поля в событиях wiSLA (`checkedAt`, `breachStartedAt`, `normalizedAt`, `receivedAt`) сериализуются как UTC instant с суффиксом `Z` (тип `Instant` на стороне NS).
  - Metadata endpoint `topics`: поле `monitorState` указывает на `wisla.monitor-state` (вместо устаревшего `monitoringState` → `wisla.monitoring-state`).
  - Удалён JSON Schema `monitoring-state-changed.schema.json`.
- migration:
  - Wisla: перестать подписываться на `wisla.monitoring-state`; обрабатывать только `wisla.monitor-state` по обновлённой схеме.
  - Парсить timestamp поля через `Instant` / UTC-`Z`, не через offset-форму.
  - Обновить потребление metadata JSON (`topics.monitorState`).

## v1.3.0 — 2026-05-28

- type: `added`
- impact: `non-breaking`
- details:
  - Добавлена периодическая публикация snapshot доступности в `wisla.availability` (availability heartbeat) при стабильном состоянии устройства.
  - Конфигурация NS: `app.integration.wisla-events.availability-heartbeat-enabled` (default `true`), `app.integration.wisla-events.availability-heartbeat-ms` (default `300000`).
  - На каждую heartbeat-отправку — новый `eventId` и актуальный `checkedAt`.
- migration:
  - Wisla: изменений контракта payload не требуется; рекомендуется `wisla.ns.integration.stale-threshold-ms` ≥ интервала heartbeat NS.
  - Ops: при необходимости отключить heartbeat — `APP_INTEGRATION_WISLA_EVENTS_AVAILABILITY_HEARTBEAT_ENABLED=false`.
