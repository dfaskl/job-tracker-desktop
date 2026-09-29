param([Parameter(Mandatory=$true)][string]$AdminEmail)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Test-Path '.env')) {
  function New-Secret { -join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) }) }
  $content = @"
ADMIN_EMAIL=$AdminEmail
APP_PORT=8080
POSTGRES_PASSWORD=$(New-Secret)
SESSION_SECRET=$(New-Secret)
ENCRYPTION_KEY=$(New-Secret)
ALLOW_REGISTRATION=true
REGISTRATION_CODE=
SESSION_DAYS=7
AI_CALLS_ENABLED=true
AI_ALLOWED_HOSTS=api.openai.com,api.deepseek.com
"@
  [IO.File]::WriteAllText((Join-Path (Get-Location) '.env'), $content, [Text.UTF8Encoding]::new($false))
  Write-Host 'Created .env with generated secrets.'
} else { Write-Host '.env already exists; keeping the existing configuration.' }
docker compose up -d --build