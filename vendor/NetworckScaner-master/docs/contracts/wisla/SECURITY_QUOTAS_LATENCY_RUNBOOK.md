# Security, Quotas, Latency, Replay Runbook (NS-5)

Runbook фиксирует эксплуатационные ограничения NS -> Wisla для MVP.

## 1. Scope and audience

Документ предназначен для владельцев интеграции Wisla и SRE/ops-ролей.
Покрывает:

- NS-1 bootstrap API (`/api/integration/wisla/v1/monitored-devices`);
- NS-2 Kafka events (`wisla.availability`, `wisla.incidents`, `wisla.monitor-state`).

## 2. Credentials model

### 2.1 REST bootstrap (NS-1)

- Аутентификация: JWT Bearer.
- Получение токена: `POST /api/auth/login`.
- Доступ к bootstrap endpoint требует роль `ADMIN`.

Код-источник:

- `backend/src/main/java/com/networkscanner/backend/users/config/SecurityConfig.java`

Рекомендуемая модель для Wisla в MVP:

- отдельный технический service-user;
- роль `ADMIN`;
- пароль хранится только в секрет-хранилище.

### 2.2 Kafka events (NS-2)

- Аутентификация и авторизация к Kafka настраиваются на брокере (SASL/SCRAM или mTLS + ACL).
- Это инфраструктурная зона, не application-level контракт NS.

## 3. Quotas and recommended backoff

### 3.1 NS-1 bootstrap API

- Максимальный `size` страницы: `500` (входная валидация endpoint).
- Рекомендуемый `size` для Wisla: `100`.
- Рекомендуемый параллелизм bootstrap/re-sync: 1 поток на задачу.

Рекомендуемый backoff при HTTP 5xx/timeout:

- `1s -> 2s -> 5s -> 15s -> 60s` (далее cap `60s`) + jitter.

### 3.2 NS-2 Kafka events

- В NS нет дополнительного application-level rate limiter для `wisla.*` publish.
- Рекомендуется задавать client-side retry/backoff у Wisla consumer.
- Рекомендуемый `max.poll.records` на стороне Wisla: `100..500`.

## 4. Latency guidance

Ориентиры (не формальный SLA):

- NS-1 bootstrap:
  - p95 <= 500 ms при `size=100`,
  - p95 <= 1500 ms при `size=500` (зависит от объёма inventory).
- NS-2 events:
  - inline post-commit publish (вариант B),
  - типовой p95 end-to-end publish <= 200 ms при здоровой Kafka.

При деградации Kafka publish ограничен timeout-конфигом (`monitoring.kafka.publisher.send-timeout-ms`, default 2000 ms).

## 5. Replay and backfill policy

### Поддерживается

1. Replay в пределах Kafka retention window:
   - Wisla управляет offset rollback/seek.
2. Re-sync текущего состояния через NS-1:
   - `GET /api/integration/wisla/v1/monitored-devices?updatedSince=...`.

### Не поддерживается (MVP)

- Исторический backfill событий, которые не были опубликованы в Kafka в момент деградации канала.
- Причина: NS-2 реализован как inline publish без transactional outbox.

Практическая стратегия recovery:

- если outage <= retention: replay Kafka;
- если outage > retention: NS-1 re-sync + согласованная ручная проверка инцидентной истории.

## 6. Operational checklist

1. Завести service-user для Wisla (роль `ADMIN`) и процедуру ротации пароля.
2. Настроить Kafka ACL для топиков:
   - `wisla.availability`
   - `wisla.incidents`
   - `wisla.monitor-state`
3. Зафиксировать retention для `wisla.*` топиков не менее 7 дней.
4. Мониторить ошибки publish (`WISLA_INTEGRATION / INTEGRATION_PUBLISH_FAILED`).
5. При изменениях credentials/limits/replay обновлять этот runbook и `CONTRACT_CHANGELOG.md`.
