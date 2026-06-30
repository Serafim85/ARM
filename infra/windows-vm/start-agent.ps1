# Запуск wisla-arm-agent на Windows VM
# Положите рядом с этим скриптом: wisla-arm-agent.exe
# С Mac: agent/dist/wisla-arm-agent.exe

$ErrorActionPreference = "Stop"
$AgentDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Exe = Join-Path $AgentDir "wisla-arm-agent.exe"

if (-not (Test-Path $Exe)) {
  Write-Host "ERROR: wisla-arm-agent.exe not found in $AgentDir" -ForegroundColor Red
  Write-Host "Copy from Mac: scp .../agent/dist/wisla-arm-agent.exe user@vm:C:\Agent\"
  exit 1
}

# Mac host (same as Linux VM — проверено curl к 192.168.64.1)
$env:WISLA_ARM_AGENT_KEY = "dev-arm-ingest-key"
$env:WISLA_ARM_SERVER_URL = "http://192.168.64.1:8081"
$env:WISLA_ARM_HOSTNAME = "pilot-windows-01"
$env:WISLA_ARM_OS_TYPE = "windows"
if (-not $env:WISLA_ARM_PRIMARY_IP) {
  $env:WISLA_ARM_PRIMARY_IP = (Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.PrefixOrigin -ne "WellKnown" } |
    Select-Object -First 1 -ExpandProperty IPAddress)
}
$env:WISLA_ARM_POLL_INTERVAL_SEC = "30"

Write-Host "Testing backend..."
try {
  $r = Invoke-WebRequest -Uri "$($env:WISLA_ARM_SERVER_URL)/api/public/app-config" -UseBasicParsing -TimeoutSec 5
  Write-Host "OK  HTTP $($r.StatusCode)" -ForegroundColor Green
} catch {
  Write-Host "FAIL: cannot reach backend at $($env:WISLA_ARM_SERVER_URL)" -ForegroundColor Red
  Write-Host "Try from Mac: backend must listen on 8081; VM network same as Linux (192.168.64.x)"
  exit 1
}

Write-Host "Starting agent host=$($env:WISLA_ARM_HOSTNAME) ip=$($env:WISLA_ARM_PRIMARY_IP) server=$($env:WISLA_ARM_SERVER_URL)"
Set-Location $AgentDir
& $Exe
