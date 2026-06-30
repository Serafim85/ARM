# WISLA ARM Agent (Go)

MVP agent: **real host metrics** (gopsutil on Linux/Windows) → HTTPS batch → backend ingest API.

Set `WISLA_ARM_USE_STUB=1` for sine-wave demo data without reading the host.

## Run (dev)

```bash
export WISLA_ARM_AGENT_KEY=dev-arm-ingest-key
export WISLA_ARM_SERVER_URL=http://localhost:8081

cd agent
go run ./cmd/wisla-arm-agent
```

## Linux VM (pilot)

1. On Mac: `./infra/build-agent-linux.sh` → `agent/dist/wisla-arm-agent-linux`
2. Copy binary to Linux VM, then:

```bash
export WISLA_ARM_AGENT_KEY=dev-arm-ingest-key
export WISLA_ARM_SERVER_URL=http://<MAC_LAN_IP>:8081
export WISLA_ARM_HOSTNAME=pilot-linux-01
export WISLA_ARM_OS_TYPE=linux
export WISLA_ARM_PRIMARY_IP=<LINUX_VM_IP>
./wisla-arm-agent-linux
```

## Windows VM (pilot)

Backend runs on the **Mac host**, not inside the VM — use the Mac **LAN IP**, not `localhost`.

1. On Mac: stack up (`docker compose`, `./infra/run-backend-dev.sh`).
2. Mac IP: `ipconfig getifaddr en0` (or Wi‑Fi interface).
3. Build exe on Mac: `./infra/build-agent-windows.sh` → `agent/dist/wisla-arm-agent.exe`.
4. Copy `wisla-arm-agent.exe` to the Windows VM (shared folder / RDP).
5. On Windows (PowerShell), test connectivity:

```powershell
.\infra\test-backend-from-windows.ps1 -BackendUrl "http://192.168.1.5:8081"
```

6. Run agent:

```powershell
$env:WISLA_ARM_AGENT_KEY = "dev-arm-ingest-key"
$env:WISLA_ARM_SERVER_URL = "http://192.168.1.5:8081"
$env:WISLA_ARM_HOSTNAME = "pilot-windows-01"
$env:WISLA_ARM_OS_TYPE = "windows"
$env:WISLA_ARM_PRIMARY_IP = "192.168.1.20"   # IP of the Windows VM
cd C:\path\to\ARM\agent\dist
.\wisla-arm-agent.exe
```

Or from repo clone on VM: `.\infra\run-agent-windows.ps1` (needs Go or prebuilt exe).

Expected log: `ingest ok metrics=3`. In UI: **АРМ** → `pilot-windows-01`, OS **Windows**, **Online**.

## Config (env)

| Variable | Default |
|---|---|
| `WISLA_ARM_AGENT_KEY` | required (or `AGENT_INGEST_API_KEY`) |
| `WISLA_ARM_SERVER_URL` | `http://localhost:8081` |
| `WISLA_ARM_OS_TYPE` | `unknown` |
| `WISLA_ARM_POLL_INTERVAL_SEC` | `60` |
| `WISLA_ARM_USE_STUB` | unset = real metrics; `1` = stub sine waves |
| `WISLA_ARM_DEMO_DISK_PCT` | Override disk % for alert demo (e.g. `96`) |
| `WISLA_ARM_DEMO_CPU_PCT` | Override CPU % (CPU alert needs 5 min sustained — use disk for live demo) |
| `WISLA_ARM_DEMO_BSOD` | `1` = send demo BSoD event |

## Tests

```bash
go test ./...
```
