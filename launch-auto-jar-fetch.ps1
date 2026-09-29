[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Commit,
    [Parameter(Mandatory = $true)][string]$Branch,
    [string]$Remote = 'origin'
)

$ErrorActionPreference = 'Stop'
$artifactDirectory = Join-Path $PSScriptRoot 'local-artifacts'
New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
$stdoutLog = Join-Path $artifactDirectory 'auto-fetch.log'
$stderrLog = Join-Path $artifactDirectory 'auto-fetch-error.log'
$worker = Join-Path $PSScriptRoot 'push-and-fetch-jar.ps1'
$arguments = @(
    '-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', ('"' + $worker + '"'),
    '-FetchOnly',
    '-Commit', $Commit,
    '-Branch', ('"' + $Branch + '"'),
    '-Remote', ('"' + $Remote + '"')
) -join ' '

Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WindowStyle Hidden `
    -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
