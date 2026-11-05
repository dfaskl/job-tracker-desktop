[CmdletBinding()]
param([string]$AppDirectory = '', [string]$JarPath = '')
$ErrorActionPreference = 'Stop'
if (-not $AppDirectory) {
    $target = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'local-deployment.json') | ConvertFrom-Json
    $AppDirectory = [string]$target.directory
}
if (-not $JarPath) { $JarPath = Join-Path $PSScriptRoot 'dist\job-tracker.jar' }
if (-not (Test-Path -LiteralPath $JarPath)) { throw 'Built JAR is missing.' }
if (-not (Test-Path -LiteralPath (Join-Path $AppDirectory 'environment.json'))) { throw 'Local deployment is not configured.' }
$jar = Join-Path $AppDirectory 'job-tracker.jar'
$rollback = Join-Path $AppDirectory 'run\rollback.jar'
& (Join-Path $AppDirectory 'stop.ps1') -AppDirectory $AppDirectory
Copy-Item -LiteralPath $jar -Destination $rollback -Force
try {
    Copy-Item -LiteralPath $JarPath -Destination $jar -Force
    foreach ($script in @('start.ps1','start-hidden.vbs','stop.ps1','install-task.ps1','configure-firewall.ps1')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot "deploy\windows\$script") -Destination (Join-Path $AppDirectory $script) -Force
    }
    & (Join-Path $AppDirectory 'install-task.ps1') -AppDirectory $AppDirectory
    Start-ScheduledTask -TaskName 'CareerFlow Local Service'
    $started = $false
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try {
            $response = Invoke-WebRequest 'http://127.0.0.1:18080/healthz' -UseBasicParsing -TimeoutSec 2 -Proxy $null
            if (($response.Content | ConvertFrom-Json).ok) { $started = $true; break }
        } catch {}
        Start-Sleep -Seconds 1
    }
    if (-not $started) { throw 'Updated local service failed its health check.' }
} catch {
    & (Join-Path $AppDirectory 'stop.ps1') -AppDirectory $AppDirectory
    Copy-Item -LiteralPath $rollback -Destination $jar -Force
    Start-ScheduledTask -TaskName 'CareerFlow Local Service'
    throw
} finally {
    if (Test-Path -LiteralPath $rollback) { Remove-Item -LiteralPath $rollback -Force }
}
$healthy = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    try {
        $response = Invoke-WebRequest 'http://127.0.0.1:18080/healthz' -UseBasicParsing -TimeoutSec 2 -Proxy $null
        if (($response.Content | ConvertFrom-Json).ok) { $healthy = $true; break }
    } catch {}
    Start-Sleep -Seconds 1
}
if (-not $healthy) { throw 'The supervised service did not become healthy.' }
Copy-Item -LiteralPath $jar -Destination (Join-Path $AppDirectory 'backups\job-tracker.jar') -Force
Write-Host "Local deployment complete: $AppDirectory"
