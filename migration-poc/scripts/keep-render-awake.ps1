param(
    [string]$HealthUrl = 'https://job-tracker-migration-poc.onrender.com/healthz',
    [ValidateRange(1, 1440)]
    [int]$IntervalMinutes = 10,
    [ValidateRange(1, 300)]
    [int]$RequestTimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
Write-Host "Keeping $HealthUrl awake every $IntervalMinutes minutes. Press Ctrl+C to stop."

while ($true) {
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -Method Get -TimeoutSec $RequestTimeoutSeconds -UseBasicParsing
        if ($response.StatusCode -ne 200) {
            Write-Warning "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') health check returned HTTP $($response.StatusCode)."
        }
    }
    catch {
        Write-Warning "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') health check failed: $($_.Exception.Message)"
    }
    if ($Once) { break }
    Start-Sleep -Seconds ($IntervalMinutes * 60)
}