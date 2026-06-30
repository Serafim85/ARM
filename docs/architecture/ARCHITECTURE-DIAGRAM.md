# Архитектурная схема WISLA АРМ

> Блок-схема компонентов, потоков данных и развёртывания MVP.  
> Краткая версия: [`../ARCHITECTURE.md`](../ARCHITECTURE.md) · Решения: [`../DECISIONS.md`](../DECISIONS.md)

---

## 1. Компоненты системы (блок-схема)

```mermaid
flowchart TB
    subgraph clients["Клиенты и операторы"]
        OP["Оператор / администратор<br/>браузер"]
        TG["Telegram<br/>оператор"]
        MAIL["Email<br/>оператор"]
    end

    subgraph agents["Рабочие станции (АРМ)"]
        A1["wisla-arm-agent<br/>Go · Windows / Linux"]
        A2["wisla-arm-agent<br/>pilot-linux-01"]
        A3["wisla-arm-agent<br/>pilot-windows-01"]
    end

    subgraph app["Приложение WISLA АРМ"]
        FE["Angular UI<br/>PrimeNG · :3000"]
        BE["Spring Boot backend<br/>profile wisla-arm · :8081"]
    end

    subgraph be_modules["Модули backend"]
        ING["agentingest<br/>POST /api/v1/agent/ingest"]
        WS["workstation<br/>реестр АРМ, метрики, логи"]
        MON["monitoring<br/>шаблоны, пороги, события"]
        NOT["notifications<br/>SMTP, Telegram"]
        USR["users<br/>JWT, LOCAL / LDAP"]
    end

    subgraph data["Данные"]
        DB[("TimescaleDB<br/>PostgreSQL 16 · :5435<br/>БД wisla_arm")]
    end

    subgraph external["Внешние сервисы (опционально)"]
        LDAP["OpenLDAP / AD<br/>:389"]
        SMTP["SMTP-сервер"]
        TAPI["Telegram Bot API"]
    end

    OP -->|"HTTPS REST + JWT"| FE
    FE -->|"REST /api/*"| BE
    BE --> ING & WS & MON & NOT & USR

    A1 & A2 & A3 -->|"HTTPS batch JSON<br/>X-Agent-Key"| ING
    ING --> WS & MON
    MON --> NOT
    WS & MON & NOT & USR --> DB

    USR -.->|"authMode LDAP"| LDAP
    NOT --> SMTP --> MAIL
    NOT --> TAPI --> TG

    style agents fill:#e8f4fd,stroke:#2563eb
    style app fill:#f0fdf4,stroke:#16a34a
    style data fill:#fef3c7,stroke:#d97706
    style external fill:#f3f4f6,stroke:#6b7280,stroke-dasharray: 5 5
```

---

## 2. Поток данных: ingest → алерт → уведомление

```mermaid
sequenceDiagram
    autonumber
    participant Agent as Go Agent
    participant Ingest as agentingest
    participant WS as workstation
    participant Mon as monitoring
    participant DB as TimescaleDB
    participant Alert as ArmWorkstationAlertService
    participant Notif as notifications
    participant UI as Angular UI
    participant TG as Telegram

    Agent->>Ingest: POST /api/v1/agent/ingest<br/>metrics, logs, events
    Ingest->>WS: upsert workstation, online
    Ingest->>DB: INSERT metric_values, log_events
    Ingest->>Alert: evaluateAfterIngest()
    Alert->>Mon: resolve arm-linux / arm-windows template
    Mon->>Mon: ThresholdEvaluationService
    Mon->>DB: monitoring_events OPEN / RESOLVED
    Alert->>Notif: notifyMonitoringEvent()
    Notif->>TG: sendMessage (если подписка TELEGRAM)
    UI->>Mon: GET /api/monitoring/events
    Mon->>DB: read events
    DB-->>UI: журнал инцидентов
```

---

## 3. Аутентификация

```mermaid
flowchart LR
    subgraph public["Без JWT"]
        ING["POST /api/v1/agent/ingest<br/>X-Agent-Key"]
        CFG["GET /api/public/app-config"]
        LOGIN["POST /api/auth/login"]
    end

    subgraph protected["С JWT Bearer"]
        ARM["GET /api/v1/workstations/**"]
        EVT["GET /api/monitoring/events"]
        ADM["/api/admin/**"]
    end

    LOGIN -->|"LOCAL или LDAP"| JWT["accessToken + roles"]
    JWT --> ARM & EVT & ADM
    ING --> KEY["AGENT_INGEST_API_KEY"]

    style public fill:#dbeafe
    style protected fill:#dcfce7
```

| Роль | Доступ |
|------|--------|
| VIEWER | Просмотр АРМ, событий, дашбордов |
| OPERATOR | + подписки на уведомления |
| ADMIN | + SMTP, LDAP, пользователи |

---

## 4. Развёртывание MVP (демо / пилот)

```mermaid
flowchart TB
    subgraph mac["MacBook (хост разработки)"]
        DOCKER["Docker<br/>wisla-arm-timescaledb :5435"]
        BACK["Spring Boot :8081"]
        FRONT["ng serve :3000"]
    end

    subgraph vms["Виртуальные машины"]
        VM1["UTM: pilot-linux-01<br/>agent → 192.168.64.1:8081"]
        VM2["UTM: pilot-windows-01<br/>agent → 192.168.64.1:8081"]
    end

    subgraph optional["Опционально (демо)"]
        LDAPC["OpenLDAP :389<br/>docker-compose.ldap.yml"]
    end

    VM1 & VM2 -->|"ingest HTTPS"| BACK
    BACK --> DOCKER
    FRONT --> BACK
    BACK -.-> LDAPC

    style mac fill:#ecfdf5
    style vms fill:#eff6ff
    style optional fill:#f9fafb,stroke-dasharray: 4 4
```

---

## 5. Модель данных (упрощённо)

```mermaid
erDiagram
    workstations ||--o{ log_events : has
    workstations ||--o{ workstation_events : has
    monitored_devices ||--o{ monitoring_events : generates
    workstations ||--|| monitored_devices : "bridge по IP"
    metric_values }o--|| workstations : "device_ip key"
    notification_subscriptions ||--o{ notifications : dispatches

    workstations {
        bigint id PK
        string hostname
        string primary_ip
        string os_type
        string status
        timestamp last_seen_at
    }

    metric_values {
        timestamp recorded_at
        string device_ip
        string metric_name
        float metric_value
    }

    monitoring_events {
        bigint id PK
        string status "OPEN | RESOLVED"
        string trigger_name
        float actual_value
        timestamp breach_started_at
    }

    notification_subscriptions {
        bigint id PK
        string channel "SMTP | TELEGRAM"
        string event_code
        string device_tag_filter
    }
```

---

## 6. Шаблоны мониторинга

```mermaid
flowchart LR
    T1["templates/arm-linux.template"]
    T2["templates/arm-windows.template"]
    AG1["Agent metrics<br/>arm.cpu.util<br/>arm.mem.used<br/>arm.disk.root.used_pct"]
    EV["monitoring_events"]
    T1 --> MON["MonitoringTemplateResolver"]
    T2 --> MON
    AG1 --> MON
    MON --> EV

    T1 -.->|"CPU > 90%"| EV
    T1 -.->|"Disk > 95%"| EV
    T2 -.->|"CPU > 90%"| EV
```

---

## 7. Порты и технологии

| Компонент | Технология | Порт / URL |
|-----------|------------|------------|
| Agent | Go, gopsutil | исходящий → backend :8081 |
| Backend | Java 17, Spring Boot 3.4, Flyway | **8081** |
| Frontend | Angular 21, PrimeNG | **3000** |
| БД | TimescaleDB PG16 | **5435** → 5432 |
| LDAP (демо) | OpenLDAP | **389** |
| Swagger | springdoc | http://localhost:8081/swagger-ui.html |

---

## 8. Что отключено в MVP (fork NetworckScaner)

| Модуль NS | Статус в WISLA АРМ |
|-----------|-------------------|
| network.scan, scanjobs | скрыто в UI |
| SNMP collector | выключено |
| Kafka pipeline | `monitoring.kafka.enabled=false` |
| WISLA integration | stub / v2 |

---

## 9. Ключевые API

| Метод | Путь | Назначение |
|-------|------|------------|
| POST | `/api/v1/agent/ingest` | Приём метрик от агента |
| POST | `/api/auth/login` | JWT (LOCAL / LDAP) |
| GET | `/api/v1/workstations` | Реестр АРМ |
| GET | `/api/monitoring/events` | Журнал инцидентов |
| GET/POST | `/api/admin/system/notification-subscriptions` | Подписки алертов |

Полный контракт ingest: `docs/architecture/архитектура-и-техническая-реализация.docx` §4.3.
