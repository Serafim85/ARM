# INTEGRATION_WISLA_BOOTSTRAP_API.md

Документ фиксирует smoke-контракт Story **NS-1** для bootstrap/re-sync интеграции NS -> Wisla.

## Endpoint

- **Method:** `GET`
- **Path:** `/api/integration/wisla/v1/monitored-devices`
- **Auth:** `Bearer JWT`, роль `ADMIN`

## Query parameters

- `page` — номер страницы, начиная с `0` (default `0`)
- `size` — размер страницы, `1..500` (default `100`)
- `updatedSince` — ISO-8601 timestamp (optional), возвращает устройства с `updatedAt >= updatedSince`

## Versioning policy

- Версия контракта в URL: `/v1/` (major changes)
- Версия схемы envelope в поле ответа `schemaVersion` (minor/additive changes)

Текущая версия envelope: `schemaVersion = "1.0"`.

## Response shape

```json
{
  "schemaVersion": "1.0",
  "sourceSystem": "networkscanner",
  "generatedAt": "2026-04-30T10:20:15.123Z",
  "page": 0,
  "size": 2,
  "totalElements": 1250,
  "totalPages": 625,
  "first": true,
  "last": false,
  "items": [
    {
      "sourceSystem": "networkscanner",
      "externalDeviceId": 101,
      "ip": "10.10.1.101",
      "hostName": "edge-sw-101",
      "name": "Edge SW 101",
      "serialNumber": "FTX00000101",
      "macAddress": "AA:BB:CC:DD:EE:11",
      "vendor": "Cisco",
      "model": "C9200L-24P",
      "firmwareVersion": "17.9.4",
      "templateIds": ["cisco-base", "cisco-extended"],
      "effectiveTemplateId": "cisco-extended",
      "templateVersion": "5",
      "packVersion": "2026.04.1",
      "schemaVersion": "2.0",
      "updatedAt": "2026-04-29T14:11:52Z"
    },
    {
      "sourceSystem": "networkscanner",
      "externalDeviceId": 102,
      "ip": "10.10.1.102",
      "hostName": "edge-sw-102",
      "name": "Edge SW 102",
      "serialNumber": "FTX00000102",
      "macAddress": "AA:BB:CC:DD:EE:12",
      "vendor": "Cisco",
      "model": "C9200L-24P",
      "firmwareVersion": "17.9.4",
      "templateIds": ["cisco-base"],
      "effectiveTemplateId": "cisco-base",
      "templateVersion": "5",
      "packVersion": "2026.04.1",
      "schemaVersion": "2.0",
      "updatedAt": "2026-04-29T14:12:03Z"
    }
  ]
}
```

## Device interfaces (wiSLA NS probe card)

- **Method:** `GET`
- **Path:** `/api/integration/wisla/v1/monitored-devices/devices/{deviceId}/interfaces`
- **Auth:** `Bearer JWT`, роль `ADMIN`
- **Behavior:** идентично `GET /api/monitoring/devices/{deviceId}/interfaces` (снимок интерфейсов из `MonitoringService`).

## Bootstrap/re-sync usage notes

1. Для полного bootstrap: запросы с `page=0`, затем `page+1` до `last=true`.
2. Для периодического re-sync: используйте `updatedSince` по watermark последнего успешного цикла.
3. Read endpoint идемпотентен: повтор запроса безопасен и не изменяет состояние NS.

## Swagger

- Swagger UI: `http://<host>:8081/swagger-ui.html`
- Описание endpoint: tag `Интеграция Wisla`
