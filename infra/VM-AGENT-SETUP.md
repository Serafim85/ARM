# Подключение агентов к VM (репетиция / демо)

> **Mac IP (обнови если сменилась сеть):** `192.168.0.118`  
> **Backend:** `http://192.168.0.118:8081`  
> **UI:** `http://localhost:3000` (только с Mac)

## 1. Mac — уже должно быть запущено

```bash
cd /Users/valentin/Projects/ARM
./infra/ensure-docker.sh --up          # или Docker Desktop + compose up
./infra/run-backend-demo.sh            # терминал 1
cd frontend && npm start               # терминал 2
./infra/demo-check.sh
```

Бинарники агентов:
- `agent/dist/wisla-arm-agent-linux-arm64` — Ubuntu **ARM** (`uname -m` = `aarch64`), типично VM на Mac M1/M2/M3
- `agent/dist/wisla-arm-agent-linux-amd64` — Ubuntu **x86_64** (`uname -m` = `x86_64`)
- `agent/dist/wisla-arm-agent.exe` — для Windows VM

---

## 2. Linux VM — `pilot-linux-01`

### Скопировать бинарник с Mac

```bash
# На Mac (подставь user@IP_ТВОЕЙ_LINUX_VM):
scp /Users/valentin/Projects/ARM/agent/dist/wisla-arm-agent-linux user@<LINUX_VM_IP>:~/
```

### На Linux VM

```bash
chmod +x ~/wisla-arm-agent-linux

export WISLA_ARM_AGENT_KEY=dev-arm-ingest-key
export WISLA_ARM_SERVER_URL=http://192.168.0.118:8081
export WISLA_ARM_HOSTNAME=pilot-linux-01
export WISLA_ARM_OS_TYPE=linux
export WISLA_ARM_PRIMARY_IP=<IP_ЭТОЙ_LINUX_VM>    # например 192.168.0.50
export WISLA_ARM_POLL_INTERVAL_SEC=30

~/wisla-arm-agent-linux
```

Ожидаемо в логе каждые 30 с: `ingest ok metrics=3`

Проверка с VM:
```bash
curl -s http://192.168.0.118:8081/api/public/app-config
```

---

## 3. Windows VM — `pilot-windows-01`

> Backend URL с Windows (как у Linux): **`http://192.168.64.1:8081`**

### Скопировать на VM

С Mac — папку `infra/windows-vm/` + exe:

```bash
scp /Users/valentin/Projects/ARM/agent/dist/wisla-arm-agent.exe \
    /Users/valentin/Projects/ARM/infra/windows-vm/start-agent.ps1 \
    user@<WINDOWS_VM_IP>:C:/Agent/
```

Или общая папка VMware/UTM: скопировать `wisla-arm-agent.exe` и `start-agent.ps1` в `C:\Agent\`.

### PowerShell на Windows (администратор не обязателen)

```powershell
cd C:\Agent

# Проверка связи
Invoke-WebRequest -Uri "http://192.168.64.1:8081/api/public/app-config" -UseBasicParsing

# Автозапуск (скрипт задаёт env)
Set-ExecutionPolicy -Scope Process Bypass
.\start-agent.ps1
```

### Вручную (если без скрипта)

```powershell
$env:WISLA_ARM_AGENT_KEY = "dev-arm-ingest-key"
$env:WISLA_ARM_SERVER_URL = "http://192.168.64.1:8081"
$env:WISLA_ARM_HOSTNAME = "pilot-windows-01"
$env:WISLA_ARM_OS_TYPE = "windows"
$env:WISLA_ARM_PRIMARY_IP = "<IP_WINDOWS_VM>"   # ipconfig → IPv4
$env:WISLA_ARM_POLL_INTERVAL_SEC = "30"

cd C:\Agent
.\wisla-arm-agent.exe
```

Ожидаемо: `ingest ok metrics=3` каждые 30 с.

**Архитектура:** exe — **x86-64** (обычная Windows VM). Если `cannot execute` — напишите, соберём arm64 Windows.

---

## 4. Проверка в UI (Mac)

1. http://localhost:3000 → login  
2. **АРМ** → фильтр поиск `pilot`  
3. `pilot-linux-01` и `pilot-windows-01` — **Online**  
4. Карточки → графики двигаются  

---

## 5. Репетиция юзкейсов (~20 мин)

См. `docs/DEMO-USE-CASES.md`. Кратко:

```bash
# На Mac — авария для UC-04
./infra/demo-trigger-disk-alert.sh pilot-linux-01
# UI → События → OPEN

./infra/demo-trigger-alert-resolve.sh pilot-linux-01
# UI → RESOLVED

# Отчёт UC-07
./infra/demo-test-export.sh
open /tmp/arm-export-test/arm-park-report.xlsx
```

---

## Troubleshooting

| Проблема | Решение |
|----------|---------|
| VM не видит Mac | Сеть VM: **Bridged**, не NAT-only |
| curl с VM timeout | macOS Firewall → разрешить Java/incoming 8081 |
| ingest failed | `WISLA_ARM_SERVER_URL` = IP Mac, не localhost |
| Старые e2e в списке | Поиск `pilot` в UI |
