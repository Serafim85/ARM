# Wisla Contract Artifacts (NS-3)

Эта директория содержит machine-readable артефакты интеграционного контракта NS -> Wisla.

## Contents

- `openapi-wisla-bootstrap.json` — snapshot OpenAPI для bootstrap API NS-1.
- `schemas/probe-availability-update.schema.json` — JSON Schema для payload `ProbeAvailabilityUpdate` (§8.3).
- `schemas/external-incident-upsert.schema.json` — JSON Schema для payload `ExternalIncidentUpsert` (§8.4).
- `schemas/monitor-state-snapshot.schema.json` — JSON Schema для per-device payload `MonitorStateSnapshot` (topic `wisla.monitor-state`).
- `IDENTIFIERS_AND_CORRELATION_RUNBOOK.md` — runbook NS-4 по lifecycle `ns_device_id`, template consistency и incident correlation.
- `SECURITY_QUOTAS_LATENCY_RUNBOOK.md` — runbook NS-5 по модели credentials, quota/backoff, latency и replay/backfill.
- `CONTRACT_VERSIONING.md` — правила эволюции контракта, breaking/deprecation policy.
- `CONTRACT_CHANGELOG.md` — changelog изменений интеграционного контракта.

## Integration scope

Контрактом NS-1 в OpenAPI считается endpoint:

- `GET /api/integration/wisla/v1/monitored-devices`

Admin smoke endpoint (`/api/admin/integration/wisla/test-event`) не является потребительским контрактом Wisla и документируется отдельно как вспомогательный.

## Release checklist

При изменении интеграционных DTO или интеграционных endpoint:

1. Обновить соответствующие JSON Schema / OpenAPI snapshot в этой директории.
2. Если изменение касается идентификаторов/корреляции, обновить `IDENTIFIERS_AND_CORRELATION_RUNBOOK.md`.
3. Если изменение касается credentials/лимитов/политики replay, обновить `SECURITY_QUOTAS_LATENCY_RUNBOOK.md`.
4. Добавить запись в `CONTRACT_CHANGELOG.md`.
5. Если изменение breaking — следовать `CONTRACT_VERSIONING.md` (migration note + уведомление Wisla).
