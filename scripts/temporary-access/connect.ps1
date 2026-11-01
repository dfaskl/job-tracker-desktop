[CmdletBinding()]
param(
    [string]$ServerAddress = '10.12.152.159',
    [string]$UserName = '',
    [ValidateRange(1, 65535)][int]$SshPort = 22,
    [ValidateRange(1, 65535)][int]$ServicePort = 18080
)

$ErrorActionPreference = 'Stop'
$tunnelProcess = $null

try {
    Write-Host 'CareerFlow temporary connection' -ForegroundColor Cyan
    Write-Host 'Keep this window open while using the website. Close it to disconnect.'
    Write-Host 'Use an authorized SSH account, not your website account. Passwords are not saved.'
    if (-not (Get-Command ssh.exe -ErrorAction SilentlyContinue)) {
        throw 'Windows OpenSSH Client is missing. Ask your administrator to install it.'
    }
    if ($ServerAddress -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$') {
        throw 'Invalid server address.'
    }
    if (-not $UserName) {
        $UserName = Read-Host 'SSH username (ask the service owner if you do not have one)'
    }
    if ($UserName -notmatch '^[A-Za-z0-9_][A-Za-z0-9._-]*$') {
        throw 'Invalid SSH username.'
    }

    # Ask Windows for an unused loopback port, avoiding existing VS Code tunnels.
    $portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $portProbe.Start()
        $localPort = $portProbe.LocalEndpoint.Port
    } finally {
        $portProbe.Stop()
    }
    $localUrl = "http://127.0.0.1:$localPort"
    $sshArguments = @(
        '-N', '-T', '-p', "$SshPort",
        '-o', 'ExitOnForwardFailure=yes',
        '-o', 'ConnectTimeout=10',
        '-o', 'ServerAliveInterval=30',
        '-o', 'ServerAliveCountMax=3',
        '-o', 'StrictHostKeyChecking=ask',
        '-L', "127.0.0.1:${localPort}:127.0.0.1:$ServicePort",
        "$UserName@$ServerAddress"
    )

    Write-Host "Connecting to $ServerAddress on SSH port $SshPort ..." -ForegroundColor Cyan
    Write-Host 'First connection: verify the host fingerprint with the service owner before typing yes.'
    Write-Host 'When SSH asks for a password, typing is invisible. Press Enter after typing it.'
    # Inherit this console so OpenSSH itself handles authentication securely.
    $tunnelProcess = Start-Process -FilePath (Get-Command ssh.exe).Source -ArgumentList $sshArguments -NoNewWindow -PassThru
    $deadline = [DateTime]::UtcNow.AddMinutes(3)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($tunnelProcess.HasExited) {
            throw 'SSH disconnected. Check the SSH error above (network, account, password, or forwarding permission).'
        }
        try {
            $health = Invoke-WebRequest -Uri "$localUrl/healthz" -UseBasicParsing -TimeoutSec 2 -Proxy $null
            if ($health.StatusCode -eq 200 -and ($health.Content | ConvertFrom-Json).ok -eq $true) {
                $ready = $true
                break
            }
        } catch {
            # Authentication and tunnel startup can take a little time.
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        throw 'The website did not become ready within 3 minutes. The service may be stopped or SSH forwarding may be disabled.'
    }

    Write-Host "Connected: $localUrl" -ForegroundColor Green
    Write-Host 'Log in with your own CareerFlow website account. Keep this window open.'
    try { Start-Process "$localUrl/#/login" } catch { Write-Host 'Open the address above in your browser.' }
    $tunnelProcess.WaitForExit()
    Write-Host 'The connection has ended. Run this script again to reconnect.' -ForegroundColor Yellow
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    if ($null -ne $tunnelProcess -and -not $tunnelProcess.HasExited) {
        Stop-Process -Id $tunnelProcess.Id -ErrorAction SilentlyContinue
    }
}
