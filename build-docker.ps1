[CmdletBinding()]
param(
    [string]$Image = ('careerflow:' + (Get-Date -Format 'yyyyMMdd-HHmmss')),
    [switch]$Deploy
)
$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot
if ($Image -notmatch '^careerflow:[a-zA-Z0-9][a-zA-Z0-9_.-]*$') { throw 'Invalid CareerFlow image tag.' }
Push-Location $repo
try {
    $engineOS = & docker info --format '{{.OSType}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker engine is unavailable. Start Docker Desktop first.' }
    if (($engineOS -join '').Trim() -ne 'linux') { throw 'Switch Docker Desktop to Linux containers before building.' }
    # Frontend tests/build and backend tests/package execute inside the build stages.
    & docker build --platform linux/amd64 --tag $Image .
    if ($LASTEXITCODE -ne 0) { throw 'Docker build failed. No deployment was attempted.' }
    New-Item -ItemType Directory -Force -Path dist | Out-Null
    $archive = Join-Path $repo 'dist/careerflow-image.tar'
    & docker image save --output $archive $Image
    if ($LASTEXITCODE -ne 0) { throw 'Image export failed.' }
    $imageId = (& docker image inspect --format '{{.Id}}' $Image).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Image inspection failed.' }
    @{image=$Image; imageId=$imageId; sha256=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()} |
        ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $repo 'dist/careerflow-image.json')
    Write-Host "Docker image package ready: $archive"
    if ($Deploy) { & (Join-Path $repo 'deploy-docker-server.ps1') }
}
finally { Pop-Location }
