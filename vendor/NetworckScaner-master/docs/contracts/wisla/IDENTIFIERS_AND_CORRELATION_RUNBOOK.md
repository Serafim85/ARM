# Identifiers and Correlation Runbook (NS-4)

Runbook фиксирует гарантии NS по идентификаторам устройств и корреляции инцидентов для интеграции с Wisla.

## 1. Scope and audience

Документ адресован владельцам интеграции Wisla (Stories 4.1, 4.3, 8.2, 8.3) и покрывает:

- bootstrap API NS-1 (`GET /api/integration/wisla/v1/monitored-devices`);
- event payload NS-2 (`ProbeAvailabilityUpdate`, `ExternalIncidentUpsert`, `MonitorStateSnapshot`).

## 2. Device identity (`sourceSystem + ns_device_id`)

### Source system

- `sourceSystem` задаётся через `app.integration.source-system` (default `networkscanner`).

### `ns_device_id` (externalDeviceId)

- В payload используется `externalDeviceId = MonitoredDeviceEntity.id`.
- `MonitoredDeviceEntity.id` — `IDENTITY`/BIGSERIAL surrogate key.

Код-источники:

- `backend/src/main/java/com/networkscanner/backend/monitoring/model/MonitoredDeviceEntity.java`
- `backend/src/main/java/com/networkscanner/backend/integration/impl/ProbeBootstrapPayloadMapper.java`

### Гарантии NS

- В рамках одного `sourceSystem` значение `externalDeviceId` уникально.
- Пока строка существует в `monitored_devices`, `externalDeviceId` закреплён за этой записью.

### Что NS не гарантирует

- Сохранение того же `externalDeviceId` после `deactivate*`:
  - в NS используется физическое удаление (`deleteAll`),
  - повторная активация может создать новую строку и новый `id`.

Код-источник:

- `backend/src/main/java/com/networkscanner/backend/monitoring/impl/MonitoringServiceImpl.java` (`deactivate`, `deactivateByIds`)

## 3. Matching rules and edge cases

При `activate(...)` NS пытается найти существующее устройство в порядке:

1. `IP`
2. `serialNumber`
3. `macAddress`

Если запись не найдена — создаётся новая entity.

Код-источник:

- `backend/src/main/java/com/networkscanner/backend/monitoring/impl/MonitoringServiceImpl.java` (`resolveExistingEntity`)

### Ожидаемое поведение в edge cases

- **Delete + re-activate**: обычно новый `externalDeviceId`.
- **IP changed, serial same**: запись может быть найдена по serial, `externalDeviceId` сохраняется.
- **MAC changed, IP same**: запись находится по IP, `externalDeviceId` сохраняется.

## 4. Template consistency (`templateId` / `templateVersion`)

### Где формируются значения

- Bootstrap NS-1 берёт `templateId/templateVersion` из `MonitoredDeviceEntity`.
- Incident payload NS-2 берёт `templateId/templateVersion` из контекста события (`EvaluatedMonitoringEvent`/`MonitoringEventEntity`) в момент детекта.

Код-источники:

- `backend/src/main/java/com/networkscanner/backend/integration/impl/ProbeBootstrapPayloadMapper.java`
- `backend/src/main/java/com/networkscanner/backend/integration/impl/ExternalIncidentUpsertMapper.java`

### Гарантия

- В рамках одного event payload `templateId/templateVersion` консистентны.

### Важное ограничение

- Между bootstrap и incident возможна смена версии шаблона, поэтому значения могут отличаться по времени.
- Для маршрутизации incident в Wisla авторитетны поля из incident payload.

## 5. Incident correlation key

NS использует составной ключ корреляции:

`triggerUuid + "|" + instanceKey + "|" + thresholdLevel + "|" + breachStartedAt`

Код-источник:

- `backend/src/main/java/com/networkscanner/backend/integration/impl/ExternalIncidentUpsertMapper.java`

### Почему не single id

- `triggerUuid` сам по себе не уникален по instance.
- Numeric primary key события не публикуется наружу.
- `eventId` в payload технический (UUID события публикации), не бизнес-идентификатор инцидента.

## 6. Alignment with Wisla Story 4.1

NS payload `ExternalIncidentUpsert` содержит mandatory поля §8.4 для идентификации target service и lifecycle-инцидента:

- `sourceSystem`, `externalDeviceId`, `templateId`, `templateVersion`, `incidentStatus`
- `triggerUuid`, `triggerName`, `metricName`, `instanceKey`
- `thresholdLevel`, `thresholdValue`, `actualValue`
- `breachStartedAt`, `normalizedAt`, `receivedAt`, `correlationKey`

Это соответствует требованиям для валидации/маппинга Wisla Story 4.1.

## 7. Replay and re-sync notes

- NS-1 bootstrap (`updatedSince`) используется Wisla для reconciliation текущего inventory.
- NS-2 stream даёт lifecycle событий; при retry/replay возможна повторная доставка.
- Для идемпотентной обработки рекомендуется использовать `correlationKey + incidentStatus` как бизнес-ключ, а `eventId` как технический dedup key.
