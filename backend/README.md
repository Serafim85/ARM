# Backend — fork NetworckScaner (WISLA ARM)

Spring Boot 3.4 · Java 17 · TimescaleDB

## Run (MVP profile)

```bash
# 1. Database
docker compose -f ../infra/docker-compose.yml up -d

# 2. Backend
../infra/run-backend-dev.sh
# or: mvn spring-boot:run -Dspring-boot.run.profiles=wisla-arm
```

Profile **`wisla-arm`**: DB `wisla_arm`, SNMP collector off, WISLA Kafka off.

Default port: **8081** (see `application-wisla-arm.properties`).

## Tests

```bash
mvn test
```

Integration (cross-stack): `../tests/integration/`

## NS modules kept / disabled in UI

- **Kept:** monitoring (.template), events, users, dashboards, notifications
- **Disabled in MVP profile:** SNMP collector, Kafka pipeline, WISLA events
- **Hidden in frontend:** scan, scan-jobs, topology (`hideNetworkScannerFeatures`)

New ARM modules (planned): `workstation`, `agent-ingest`.
