[CmdletBinding()]
param([string]$AppDirectory = '', [switch]$Watch)
$ErrorActionPreference = 'Stop'
if (-not $AppDirectory) { $AppDirectory = $PSScriptRoot }
$AppDirectory = [IO.Path]::GetFullPath($AppDirectory)
$configuration = Get-Content -Raw -LiteralPath (Join-Path $AppDirectory 'environment.json') | ConvertFrom-Json
foreach ($entry in $configuration.PSObject.Properties) {
    [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, 'Process')
}
$runtime = Get-Content -Raw -LiteralPath (Join-Path $AppDirectory 'deployment.json') | ConvertFrom-Json
$java = [string]$runtime.java
$jar = Join-Path $AppDirectory 'job-tracker.jar'
if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $jar)) { throw 'Java or application JAR is missing.' }
New-Item -ItemType Directory -Force -Path (Join-Path $AppDirectory 'run'), (Join-Path $AppDirectory 'logs') | Out-Null
$pidFile = Join-Path $AppDirectory 'run\app.pid'
$mutex = New-Object Threading.Mutex($false, 'Global\CareerFlowLocalService')
$held = $false
try {
    try { $held = $mutex.WaitOne(5000) } catch [Threading.AbandonedMutexException] { $held = $true }
    if (-not $held) {
        if ($Watch) { throw 'Another service launcher is still running; retry after it exits.' }
        $response = Invoke-WebRequest "http://127.0.0.1:$($env:APP_PORT)/healthz" -UseBasicParsing -TimeoutSec 5 -Proxy $null
        if (-not ($response.Content | ConvertFrom-Json).ok) { throw 'Another launcher exists but the service is not healthy.' }
        Write-Host 'The supervised local service is already healthy.'
        return
    }
    do {
        $running = $null
        if (Test-Path -LiteralPath $pidFile) {
            $serviceProcessId = 0
            if ([int]::TryParse((Get-Content -Raw -LiteralPath $pidFile).Trim(), [ref]$serviceProcessId)) {
                $candidate = Get-CimInstance Win32_Process -Filter "ProcessId=$serviceProcessId" -ErrorAction SilentlyContinue
                if ($candidate -and $candidate.CommandLine -like "*$jar*") {
                    $running = Get-Process -Id $serviceProcessId -ErrorAction SilentlyContinue
                }
            }
        }
        if (-not $running) {
            if (Get-NetTCPConnection -LocalPort ([int]$env:APP_PORT) -State Listen -ErrorAction SilentlyContinue) {
                throw 'The application port is already in use by another process.'
            }
            # Keep logs bounded across restarts without retaining extra log generations.
            foreach ($name in @('app.log', 'error.log')) {
                $log = Join-Path $AppDirectory "logs\$name"
                if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).Length -gt 10MB) { Clear-Content -LiteralPath $log }
            }
            $arguments = '-Xms256m -Xmx1024m -Duser.timezone=Asia/Shanghai -jar "' + $jar + '" --server.address=0.0.0.0 --server.port=' + $env:APP_PORT
            $running = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $AppDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $AppDirectory 'logs\app.log') -RedirectStandardError (Join-Path $AppDirectory 'logs\error.log')
            [IO.File]::WriteAllText($pidFile, [string]$running.Id)
        }
        $healthy = $false
        for ($attempt = 0; $attempt -lt 45; $attempt++) {
            if ($running.HasExited) { break }
            try {
                $response = Invoke-WebRequest "http://127.0.0.1:$($env:APP_PORT)/healthz" -UseBasicParsing -TimeoutSec 2 -Proxy $null
                if (($response.Content | ConvertFrom-Json).ok) { $healthy = $true; break }
            } catch {}
            Start-Sleep -Seconds 1
        }
        if (-not $healthy) {
            if (-not $running.HasExited) { Stop-Process -Id $running.Id -Force }
            if (-not $Watch) { throw 'Local service health check failed. See logs/app.log and logs/error.log.' }
        } else {
            Write-Host "Local service ready: http://127.0.0.1:$($env:APP_PORT) (PID=$($running.Id))"
            if ($Watch) {
                while (-not $running.WaitForExit(5000)) {}
            }
        }
        if ($Watch) { Start-Sleep -Seconds 10 }
    } while ($Watch)
} finally {
    if ($held) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
