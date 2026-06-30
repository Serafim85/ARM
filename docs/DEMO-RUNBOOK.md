# Демо WISLA АРМ — runbook

> **Документы демо:** [DEMO-USE-CASES.md](DEMO-USE-CASES.md) · [DEMO-PRESENTATION.md](DEMO-PRESENTATION.md) · [DEMO-DASHBOARDS.md](DEMO-DASHBOARDS.md) (как объяснять дашборды)  
> **Дата демо:** завтра  
> **Стенд:** Mac (хост) + 2 локальные VM (Linux + Windows)  
> **Формат:** удалённое демо, **шаринг экрана** с ноутбука (мобильный браузер / Telegram с телефона — опционально)  
> **Swagger / WISLA Kafka:** не показываем — в roadmap v2, не блокер для АРМ-мониторинга.

---

## Удалённое демо (шаринг экрана)

### Что показываем на экране

| Показывать | Не показывать |
|---|---|
| Браузер **http://localhost:3000** на весь экран | Терминалы с логами (только если что-то сломалось) |
| Список АРМ → карточка → графики → **События** | `curl`, SQL, отладку ingest |
| Меню **События** при аварии (диск / offline) | 20 хостов `e2e-*` в списке — почисти заранее |
| (Опционально) DevTools → **iPhone** 30 с | Настройку Telegram/SMTP на глазах у заказчика |

### За 30 мин до звонка

1. `docker compose up -d` + `./infra/run-backend-demo.sh` + `npm start` в frontend
2. Оба агента на VM уже **Online** (`pilot-linux-01`, `pilot-windows-01`)
3. `./infra/demo-check.sh` — зелёный
4. Прогон аварии один раз: `./infra/demo-trigger-disk-alert.sh` → видно в **События** → `./infra/demo-trigger-alert-resolve.sh`
5. Закрой лишние вкладки, уведомления macOS (Do Not Disturb)
6. Масштаб браузера **100–110%** — мелкий текст на записи не читается

### Мобилка на удалённом демо

Отдельный телефон **не нужен**. Достаточно:

- **Chrome DevTools** → Toggle device toolbar → iPhone → открыть `/workstations` и карточку  
  Фраза: *«веб адаптирован под мобильный браузер оператора»*

**Telegram:** если настроен — либо **Telegram Web** в отдельной вкладке (заранее открыт чат с ботом), либо **скриншот** уведомления как запасной слайд. Live push с телефона на шаринге неудобен.

### Сценарий на шаринге (~20 мин, только браузер)

1. Login → **АРМ** (2 Online)
2. **pilot-linux-01** — графики, логи
3. **События** → показать OPEN (или триггернуть диск скриптом **до** звонка и оставить открытым)
4. Resolve / нормальные метрики
5. **pilot-windows-01** — кросс-платформа
6. (30 с) DevTools mobile viewport
7. Roadmap v1/v2

### Fallback без терминала на глазах

- Аварию **заранее** создать и показать готовое событие в **События**
- Если VM отвалилась — агент на Mac: `WISLA_ARM_HOSTNAME=pilot-linux-01 ./infra/run-agent-dev.sh`
- Скриншоты Online + графиков в закладках

---

## Что показываем честно

| Уровень | Формулировка для заказчика |
|---|---|
| **MVP (готово)** | Агент → ingest → БД → UI: реестр АРМ, карточка, графики CPU/RAM/диск, логи, BSoD-события, offline, алерты (Email/Telegram) |
| **v1 (задел / частично)** | Реальные метрики с ОС, пилот 2 VM, **XLSX + рекомендации**, LDAP в UI |
| **v1 (после демо)** | Policy API, полные логи, 50+ АРМ, retention jobs |
| **v2** | Интеграция WISLA, SSO, S.M.A.R.T., 5000 АРМ |

Не обещайте на демо: политики с сервера, PDF-отчёты, Security log, масштаб 50+ АРМ.

---

## Сегодня — подготовка и тест (чеклист)

### A. Mac (хост, ~30 мин)

```bash
cd /Users/valentin/Projects/ARM

# 1. БД
docker compose -f infra/docker-compose.yml up -d

# 2. Backend (терминал 1) — demo profile: offline за 2 мин
TELEGRAM_ENABLED=true TELEGRAM_BOT_TOKEN=<token> ./infra/run-backend-demo.sh

# 3. Frontend (терминал 2) — порт 3000
cd frontend && npm start

# 4. Preflight
./infra/demo-check.sh

# 5. Сборка агентов для VM
./infra/build-agent-windows.sh
./infra/build-agent-linux.sh   # linux amd64 binary для VM
```

Узнай IP Mac для VM: `ipconfig getifaddr en0` → запиши как `MAC_IP`.

### B. Linux VM (~20 мин)

```bash
# Скопировать agent/dist/wisla-arm-agent-linux на VM
chmod +x wisla-arm-agent-linux

export WISLA_ARM_AGENT_KEY=dev-arm-ingest-key
export WISLA_ARM_SERVER_URL=http://<MAC_IP>:8081
export WISLA_ARM_HOSTNAME=pilot-linux-01
export WISLA_ARM_OS_TYPE=linux
export WISLA_ARM_PRIMARY_IP=<LINUX_VM_IP>
export WISLA_ARM_POLL_INTERVAL_SEC=30

./wisla-arm-agent-linux
```

Ожидаемо: `ingest ok metrics=3` каждые 30 с.

### C. Windows VM (~20 мин)

PowerShell:

```powershell
$env:WISLA_ARM_AGENT_KEY = "dev-arm-ingest-key"
$env:WISLA_ARM_SERVER_URL = "http://<MAC_IP>:8081"
$env:WISLA_ARM_HOSTNAME = "pilot-windows-01"
$env:WISLA_ARM_OS_TYPE = "windows"
$env:WISLA_ARM_PRIMARY_IP = "<WINDOWS_VM_IP>"
$env:WISLA_ARM_POLL_INTERVAL_SEC = "30"

cd C:\path\to\agent\dist
.\wisla-arm-agent.exe
```

Проверка с VM: `.\infra\test-backend-from-windows.ps1 -BackendUrl "http://<MAC_IP>:8081"`

### D. UI smoke (~10 мин)

1. http://localhost:3000 — login `admin@example.com` / `password`
2. **АРМ** → `pilot-linux-01` и `pilot-windows-01` — **Online**
3. Карточки: графики **меняются** (реальные метрики, не синусоида stub)
4. Внизу: **События** / **Логи** (логи heartbeat от агента)
5. (Опционально) BSoD demo: на одной VM `WISLA_ARM_DEMO_BSOD=1` + перезапуск агента
6. **Экспорт:** кнопка **«Экспорт XLSX»** на странице АРМ (лист «Рекомендации») или `./infra/demo-test-export.sh`

### E. Автотесты (Mac)

```bash
./tests/run-all.sh --smoke
```

### F. Очистка списка (опционально)

В UI много старых `e2e-*` / `smoke-*` — для демо отфильтруй по `pilot-` или почисти БД:

```sql
-- только если нужен чистый список; осторожно
DELETE FROM workstations WHERE hostname LIKE 'e2e-%' OR hostname LIKE 'smoke-%';
```

### G. Алерты и уведомления (сегодня обязательно)

**1. Подписки (Telegram или SMTP):**

```bash
# Вариант A — Telegram (удобнее на демо)
TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... ./infra/demo-setup-notifications.sh
# Backend уже с TELEGRAM_ENABLED=true (см. run-backend-demo.sh)

# Вариант B — только UI (без push)
# Ничего не настраивать — алерты видны в меню «События»
```

**2. Прогон аварии «диск критический» (мгновенно):**

```bash
./infra/demo-trigger-disk-alert.sh pilot-linux-01
# UI → События → OPEN → «ARM Linux: Root disk space critical»
# Telegram/Email — если настроены

./infra/demo-trigger-alert-resolve.sh pilot-linux-01
# Событие → RESOLVED
```

**3. Прогон с VM (без curl):**

```bash
# На Linux VM — остановить агент, затем:
export WISLA_ARM_DEMO_DISK_PCT=96
./wisla-arm-agent-linux
# Через 30 с — тот же алерт. Снять: unset WISLA_ARM_DEMO_DISK_PCT и перезапуск
```

**4. Прогон offline (2 мин с demo-backend):**

```bash
# Остановить агент на pilot-windows-01 (Ctrl+C)
# Подождать ~2–3 мин → статус Offline + алерт «workstation offline»
# Запустить агент снова → RESOLVED
```

**5. BSoD (авария на карточке, не monitoring-event):**

```bash
export WISLA_ARM_DEMO_BSOD=1
# перезапуск агента → карточка АРМ → блок «События» → BSOD
```

---

## Завтра — сценарий демо (~25 мин)

| Мин | Действие | Что сказать |
|---|---|---|
| 0–2 | Login, список **АРМ** | «Единый реестр рабочих станций, статус Online/Offline по heartbeat агента» |
| 2–5 | **pilot-linux-01** Online, карточка | «Linux АРМ: CPU, RAM, диск с реального хоста, история в TimescaleDB» |
| 5–8 | Графики за час / сутки | «Пороги из Zabbix-шаблона arm-linux, те же ключи что у агента» |
| 8–10 | Блок **Логи** / **BSoD** на карточке | «Агент шлёт warning/error и критические события ОС» |
| 10–13 | **Авария: диск** | `./infra/demo-trigger-disk-alert.sh` **или** `WISLA_ARM_DEMO_DISK_PCT=96` на VM → **События** → HIGH |
| 13–15 | **Уведомление** | Показать Telegram/почту **или** экран «События» если push не настроен |
| 15–17 | **Восстановление** | `./infra/demo-trigger-alert-resolve.sh` → RESOLVED |
| 17–19 | **Offline** (опционально) | Стоп агента на Windows → 2 мин → Offline + алерт connectivity |
| 19–21 | **pilot-windows-01** карточка | Кросс-платформа, тот же pipeline |
| 21–23 | **Экспорт XLSX** | Кнопка на **АРМ** → лист «Рекомендации» с авто-советами |
| 23–25 | Roadmap | v1: policy, 50+ АРМ; v2: WISLA |

### Fallback если сеть VM ↔ Mac ломается

- Показать `pilot-linux-01` с Mac: `WISLA_ARM_HOSTNAME=pilot-linux-01 WISLA_ARM_OS_TYPE=linux ./infra/run-agent-dev.sh`
- Ingest fixture: `curl -d @tests/fixtures/ingest-batch-linux.json ...`
- Заранее сделай **скриншоты** Online + графиков

### Учётные данные

| | |
|---|---|
| UI | `admin@example.com` / `password` |
| Agent key | `dev-arm-ingest-key` |
| Backend | http://localhost:8081 (с Mac), http://`<MAC_IP>`:8081 (с VM) |
| Frontend | http://localhost:3000 |

---

## Риски

| Риск | Митигация |
|---|---|
| VM не видит Mac | Bridged network, firewall 8081, `demo-check.sh` |
| DB down | `docker compose up -d` до демо |
| Порт 3000 занят | `lsof -ti :3000 \| xargs kill` → один `npm start` |
| Старый frontend без логов | Hard refresh, пересборка |
| Алерты не настроены | Показать **События** в UI — это полноценный алерт, push опционален |
| Offline долго ждать | Запускать `./infra/run-backend-demo.sh` (порог 2 мин, не 10) |

---

## После демо (v1 backlog на 2–3 мес)

1. Policy API + pull-config  
2. Реальные collectors: службы, Event Log / journald  
3. Reports CSV/XLSX + рекомендации  
4. Retention jobs  
5. Agent packages (deb/msi) + update endpoint  
6. Пилот 50+ АРМ  
