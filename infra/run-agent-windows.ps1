# Run wisla-arm-agent on Windows VM (PowerShell).
# Prerequisites: Go installed OR copy wisla-arm-agent.exe from Mac (infra/build-agent-windows.sh).
#
# Example (backend on Mac at 192.168.1.5):
#   $env:WISLA_ARM_SERVER_URL = "http://192.168.1.5:8081"
#   $env:WISLA_ARM_HOSTNAME = "pilot-windows-01"
#   $env:WISLA_ARM_PRIMARY_IP = "192.168.1.20"   # IP of this Windows VM
#   .\infra\run-agent-windows.ps1

$ErrorActionPreference = "Stop"

if (-not $env:WISLA_ARM_AGENT_KEY) {
  $env:WISLA_ARM_AGENT_KEY = "dev-arm-ingest-key"
}
if (-not $env:WISLA_ARM_SERVER_URL) {
  Write-Host "Set WISLA_ARM_SERVER_URL to your backend, e.g. http://192.168.1.5:8081 (Mac LAN IP, not localhost)" -ForegroundColor Yellow
  exit 1
}
if (-not $env:WISLA_ARM_HOSTNAME) {
  $env:WISLA_ARM_HOSTNAME = "pilot-windows-01"
}
if (-not $env:WISLA_ARM_OS_TYPE) {
  $env:WISLA_ARM_OS_TYPE = "windows"
}
if (-not $env:WISLA_ARM_POLL_INTERVAL_SEC) {
  $env:WISLA_ARM_POLL_INTERVAL_SEC = "30"
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$agentDir = Join-Path $repoRoot "agent"
$exe = Join-Path $agentDir "dist\wisla-arm-agent.exe"

Write-Host "hostname=$($env:WISLA_ARM_HOSTNAME) server=$($env:WISLA_ARM_SERVER_URL) os=$($env:WISLA_ARM_OS_TYPE)"

if (Test-Path $exe) {
  Set-Location $agentDir
  & $exe
} elseif (Get-Command go -ErrorAction SilentlyContinue) {
  Set-Location $agentDir
  go run ./cmd/wisla-arm-agent
} else {
  Write-Host "No wisla-arm-agent.exe and Go not found. On Mac run: ./infra/build-agent-windows.sh and copy agent/dist/ to VM." -ForegroundColor Red
  exit 1
}
