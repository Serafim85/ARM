# infra/

## MVP (TimescaleDB only)

```bash
docker compose -f infra/docker-compose.yml up -d
```

| Service | Host port | Credentials |
|---|---|---|
| TimescaleDB PG16 | 5435 | DB/user/pass: `wisla_arm` |

## Backend dev

```bash
./infra/run-backend-dev.sh
```

Profile `wisla-arm` — see `backend/README.md`.

## Full stack (optional, v1)

```bash
docker compose -f infra/docker-compose.yml -f infra/docker-compose.full.yml up -d
```

Adds Kafka (+ Zookeeper). LDAP — copy from `vendor/NetworckScaner-master/docker-compose.yml` when needed.

## Frontend dev

```bash
cd frontend && npm ci && npm start
```
