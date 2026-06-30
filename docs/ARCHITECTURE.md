# Architecture — WISLA АРМ (MVP)

> Living document. Update when fork lands. Canonical decisions: `docs/DECISIONS.md`.  
> Repo zones: `docs/REPO-LAYOUT.md`.

---

## High-level

> **Подробные блок-схемы (Mermaid):** [`docs/architecture/ARCHITECTURE-DIAGRAM.md`](architecture/ARCHITECTURE-DIAGRAM.md)

```text
┌─────────────┐     HTTPS batch JSON      ┌──────────────────────────────┐
│ Go Agent    │ ─────────────────────────► │ Spring Boot (fork NS)        │
│ Win / Linux │      X-Agent-Key           │ workstation + agent-ingest   │
└─────────────┘                            └───────────┬──────────────────┘
                                                       │
                       ┌───────────────────────────────┼───────────────────┐
                       ▼                               ▼                   ▼
              TimescaleDB (PG16)              Angular UI              Email/Telegram
              wisla_arm :5435                   PrimeNG                 (NS modules)
              metric_values (hypertable)
```

---

## Repo layout (CODE zone)

```text
agent/                      # Go agent
  cmd/wisla-arm-agent/
  internal/{config,collector,transport,buffer,platform/}

backend/                    # fork from vendor/NetworckScaner-master/backend
  .../workstation/
  .../agentingest/
  src/main/resources/db/migration/

frontend/                   # fork from vendor/NetworckScaner-master/frontend
  src/app/workstations/

templates/                  # arm-linux.template, arm-windows.template

infra/                      # docker-compose.yml, deploy scripts

tests/                      # e2e, integration, fixtures (not unit tests)

vendor/NetworckScaner-master/   # reference only — do not edit for ARM features
```

---

## Module boundaries

| Module | Responsibility |
|---|---|
| `agent` | Collect metrics/events/logs; buffer; POST to ingest |
| `agent-ingest` | Auth API-key; validate batch; write DB; trigger evaluation |
| `workstation` | CRUD registry; online/offline; card API |
| `monitoring` (NS) | `.template` load; threshold evaluation |
| `events` (NS) | Alerts, alarm journal |
| `notifications` (NS) | SMTP, Telegram |

**Disabled from NS:** `network.scan`, scanjobs, topology, cisco inventory.

---

## Agent ingest contract (draft)

- **Method:** `POST /api/v1/agent/ingest` (confirm in ADR when implementing)
- **Headers:** `X-Agent-Key`, `Content-Type: application/json`
- **Body:** batch with `hostname`, `timestamp`, `metrics[]`, `events[]`, `logs[]`, `agent_version`
- **Metric keys:** must match `templates/arm-*.template` items

Full draft: `docs/architecture/архитектура-и-техническая-реализация.docx` §4.3.

---

## Database (see ADR-004)

- Engine: TimescaleDB / PostgreSQL 16
- JDBC: `jdbc:postgresql://localhost:5435/wisla_arm`
- Hypertable: `metric_values`
- App tables: `workstations`, `agent_credentials`, `monitoring_events`, `log_events`, …

Detail: `docs/requirements/дорожная-карта.yaml` → `infrastructure`.

---

## Extension ports

```java
public interface AgentIngestPort { ... }
public interface WislaIntegrationPort { ... }
public class NoOpWislaIntegration implements WislaIntegrationPort { ... }
```

---

## References

- `vendor/NetworckScaner-master/README.md` — DB, metric_values, docker-compose
- `vendor/NetworckScaner-master/docker-compose.yml` — timescaledb service
- `docs/requirements/дорожная-карта.yaml` — phases, deliverables
- `design/mockups/` — UI references for frontend
