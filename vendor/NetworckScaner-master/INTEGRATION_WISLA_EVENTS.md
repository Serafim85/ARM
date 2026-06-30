# INTEGRATION_WISLA_EVENTS.md

Событийный контракт Story NS-2 для интеграции NS -> Wisla.

## Kafka topics

- `wisla.availability`
- `wisla.incidents`
- `wisla.monitor-state` (per-device monitoring state, retention 1GB / 14 days)

Для каждого топика создаётся соответствующий DLT-топик с суффиксом `.DLT`.

## Delivery model

- Семантика: at-least-once на стороне брокера/producer retry.
- Ключ сообщения: `sourceSystem|externalDeviceId` (стабильная партиция на устройство).
- Реализация MVP: inline post-commit publish через `WislaEventBridge` (`@TransactionalEventListener`).
- При длительной недоступности Kafka события могут быть потеряны; восстановление текущего состояния выполняется через NS-1 bootstrap/re-sync API.

### Periodic availability heartbeat (v1.3+)

Помимо публикации при **смене** доступности устройства, NS может периодически отправлять текущий snapshot в `wisla.availability`, чтобы потребитель (wiSLA) обновлял агрегированный статус сервиса без ожидания flip UP/DOWN.

- Включение: `app.integration.wisla-events.availability-heartbeat-enabled` (default `true`).
- Интервал: `app.integration.wisla-events.availability-heartbeat-ms` (default `300000` — 5 минут).
- На каждую отправку генерируется новый `eventId` и актуальный `checkedAt` (время цикла availability refresh).
- Рекомендуется интервал heartbeat **≤** `wisla.ns.integration.stale-threshold-ms` на стороне wiSLA (по умолчанию также 5 минут).
- Семантика at-least-once сохраняется; дедупликация на стороне wiSLA — по `eventId` и бизнес-ключу `sourceSystem + externalDeviceId + checkedAt`.

## Envelope and versioning

Все payload содержат:

- `schemaVersion` (текущее: `"1.0"`)
- `eventId` (UUID)
- `sourceSystem`

Для breaking changes рекомендуется новый параллельный топик с версией (`*.v2`).

Временные поля в событиях сериализуются как **UTC instant** с суффиксом `Z` (под `java.time.Instant` / `Instant.parse` на стороне потребителя).

## Payloads

### ProbeAvailabilityUpdate (§8.3)

```json
{
  "schemaVersion": "1.0",
  "eventId": "7f1cd6ab-36d4-4bc9-9a69-2f1d2ce2f713",
  "sourceSystem": "networkscanner",
  "externalDeviceId": 101,
  "availability": "AVAILABLE",
  "checkedAt": "2026-04-30T11:02:41Z"
}
```

### ExternalIncidentUpsert (§8.4)

```json
{
  "schemaVersion": "1.0",
  "eventId": "efee65c4-03d3-4b4f-97c5-7a4fa60484a8",
  "sourceSystem": "networkscanner",
  "externalDeviceId": 101,
  "templateId": "cisco-base",
  "templateVersion": "5",
  "incidentStatus": "OPEN",
  "triggerUuid": "tr-0001",
  "triggerName": "CPU high",
  "metricName": "cpu.util",
  "instanceKey": "global",
  "thresholdLevel": "WARNING",
  "thresholdValue": 80.0,
  "actualValue": 92.5,
  "breachStartedAt": "2026-04-30T10:58:12Z",
  "normalizedAt": null,
  "receivedAt": "2026-04-30T11:02:41Z",
  "correlationKey": "tr-0001|global|WARNING|2026-04-30T10:58:12Z",
  "severity": "WARNING",
  "triggerExpression": "last(/cpu.util)>80",
  "recoveryExpression": "last(/cpu.util)<75",
  "recoveryPath": null,
  "packVersion": "2026.04.1"
}
```

### MonitorStateSnapshot (topic `wisla.monitor-state`)

`MONITOR_ON` — полный блок `device`:

```json
{
  "schemaVersion": "1.0",
  "eventId": "a9f6a659-6f95-4c43-abfd-249f7df6145f",
  "sourceSystem": "networkscanner",
  "externalDeviceId": 101,
  "device": {
    "state": "MONITOR_ON",
    "name": "Switch 101",
    "ipAddress": "10.0.0.101",
    "templateIds": ["cisco-base:5", "cisco-if:5"],
    "defaultTemplateVersion": "5"
  }
}
```

`MONITOR_OFF` — снятие всех шаблонов, запись устройства **остаётся** в NS (`device` только `state`):

```json
{
  "schemaVersion": "1.1",
  "eventId": "b1c2d3e4-f5a6-7890-abcd-ef1234567890",
  "sourceSystem": "networkscanner",
  "externalDeviceId": 101,
  "device": {
    "state": "MONITOR_OFF"
  }
}
```

`DELETED` — физическое удаление устройства из NS (`deactivate` / UI «Удалить»); wiSLA архивирует probe/services и удаляет tickets:

```json
{
  "schemaVersion": "1.1",
  "eventId": "c3d4e5f6-a7b8-9012-cdef-345678901234",
  "sourceSystem": "networkscanner",
  "externalDeviceId": 101,
  "device": {
    "state": "DELETED"
  }
}
```

## Idempotency hints for Wisla

- `eventId` можно использовать как технический dedup key.
- Для бизнес-idempotency рекомендуется:
  - availability: `sourceSystem + externalDeviceId + checkedAt`
  - incident: `sourceSystem + externalDeviceId + correlationKey + incidentStatus`
  - monitor-state: `sourceSystem + externalDeviceId + device.state + eventId` (или собственная дедупликация на стороне wiSLA)

## Smoke test endpoint

- `POST /api/admin/integration/wisla/test-event?type=availability|incident|state`
- Доступ: `ADMIN`
