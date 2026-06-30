# Project STATUS

> **Agents: update this file at the end of every coding session.**

**Last updated:** 2026-06-23  
**Current phase:** MVP (prototype) — финальные DoD §4

---

## Summary

| Area | State |
|---|---|
| Backend fork | ✅ + ingest API `POST /api/v1/agent/ingest` |
| Go agent | ✅ stub collector (+ disk metric), HTTPS batch client |
| ARM `.template` | ✅ `templates/arm-linux`, `arm-windows` — ZABBIX_PASSIVE items |
| Infra | ✅ docker-compose TimescaleDB |
| Smoke automation | ✅ `infra/smoke-stack.sh` — **SMOKE OK** 2026-06-23 |
| MVP progress | **9.5 / 10** |

---

## Done

| Date | Item | Notes |
|---|---|---|
| 2026-06-23 | Logs + BSoD в карточке АРМ | `GET .../logs`, `GET .../events`, V67, UI telemetry |
| 2026-06-23 | Workstation metrics charts | `GET /api/v1/workstations/{id}/metrics`, карточка АРМ |
| 2026-06-23 | Workstations list API + UI | `GET /api/v1/workstations`, `/workstations` page |
| 2026-06-23 | ARM monitoring templates | `templates/arm-linux.template`, `arm-windows.template`, manifest; `ArmMonitoringTemplateTest` |
| 2026-06-23 | Agent ingest API | V66 workstations, `agentingest` + `workstation` modules, unit tests |
| 2026-06-23 | Go agent skeleton | `cmd/wisla-arm-agent`, transport, stub collector |
| 2026-06-22 | Fork NS backend/frontend | profile `wisla-arm` |
| 2026-06-22 | infra/docker-compose.yml | wisla_arm:5435 |

---

## In progress

| Item | Notes |
|---|---|
| **MVP test gate (#10)** | ✅ smoke зелёный при поднятом стеке |
| **DoD §4** | ✅ логи + BSoD в карточке; mobile layout базово |
| **Пилот 2 VM (#9)** | отложен — Win + Linux VM у вас есть |

---

## Backlog (MVP)

1. ~~Fork NS~~ ✅
2. ~~docker-compose~~ ✅
3. ~~Flyway V66 workstations~~ ✅ (run on first backend start)
4. ~~Go agent skeleton~~ ✅
5. ~~Agent ingest API~~ ✅
6. ~~`.template` linux/windows~~ ✅
7. ~~UI workstations list~~ ✅ (карточка + графики CPU/RAM/диск)
8. ~~Alerts Email/Telegram~~ ✅
9. 2 VM pilot soak
10. ~~MVP test gate~~ ✅ (smoke; алерты — финальная проверка позже)

---

## Run stack

См. **[`docs/SMOKE-TEST.md`](SMOKE-TEST.md)**.

```bash
./infra/smoke-stack.sh       # DB + backend + ingest + SQL verify
./infra/run-agent-dev.sh     # Go agent (после smoke)
```

---

## Session log

| Date | Summary | Verify |
|---|---|---|
| 2026-06-23 | E2E smoke specs enabled (login, list, card) | `E2E_STACK_UP=1 npm run test:smoke` in `tests/e2e` |
| 2026-06-23 | Workstations list API + Angular UI | `mvn test -Dtest=WorkstationStatusSupportTest`; `WorkstationListIT` when stack up |
| 2026-06-23 | MVP smoke passed | `./infra/smoke-stack.sh` — ingest → DB OK |
| 2026-06-23 | Ingest API + Go agent stub | `mvn test -Dtest=AgentIngest*`; `go test ./...` |
