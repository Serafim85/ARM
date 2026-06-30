# Quick connectivity check from Windows VM before starting the agent.
# Usage: .\infra\test-backend-from-windows.ps1 -BackendUrl "http://192.168.1.5:8081"

param(
  [Parameter(Mandatory = $true)]
  [string]$BackendUrl
)

$BackendUrl = $BackendUrl.TrimEnd("/")
Write-Host "Testing $BackendUrl/api/public/app-config ..."
try {
  $resp = Invoke-WebRequest -Uri "$BackendUrl/api/public/app-config" -UseBasicParsing -TimeoutSec 5
  Write-Host "OK HTTP $($resp.StatusCode)" -ForegroundColor Green
  Write-Host $resp.Content
} catch {
  Write-Host "FAILED: $_" -ForegroundColor Red
  Write-Host "Check: backend running on Mac, docker DB up, Mac firewall allows 8081, VM network (Bridged not NAT-only)."
  exit 1
}
