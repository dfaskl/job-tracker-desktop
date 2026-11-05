[CmdletBinding()]
param([string]$AppDirectory = 'D:\CareerFlow')
$ErrorActionPreference = 'Stop'
try {
    $runtime = Get-Content -Raw -LiteralPath (Join-Path $AppDirectory 'deployment.json') | ConvertFrom-Json
    $ruleName = 'CareerFlow-Local-LAN-18080'
    $existing = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
    if ($existing) { $existing | Remove-NetFirewallRule }
    New-NetFirewallRule -Name $ruleName -DisplayName 'CareerFlow LAN TCP 18080' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 18080 -RemoteAddress LocalSubnet -Profile Any -Program ([string]$runtime.java) | Out-Null
    'success' | Set-Content -LiteralPath (Join-Path $AppDirectory 'run\firewall-result.txt')
} catch {
    $_.Exception.Message | Set-Content -LiteralPath (Join-Path $AppDirectory 'run\firewall-result.txt')
    exit 1
}
