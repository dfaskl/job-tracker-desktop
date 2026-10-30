[CmdletBinding()]
param(
    [string]$Server = 'zhoujiajun@10.12.152.159',
    [string]$RemoteDirectory = '/home/zhoujiajun/jobTracker',
    [string]$IdentityFile = (Join-Path $env:USERPROFILE '.ssh/jobtracker_server')
)
$ErrorActionPreference = 'Stop'
if ($Server -notmatch '^[A-Za-z0-9._-]+(@[A-Za-z0-9.-]+)?$' -or
    $RemoteDirectory -notmatch '^/[A-Za-z0-9._/-]+$') { throw 'Invalid server or deployment path.' }
$archive = Join-Path $PSScriptRoot 'dist/careerflow-image.tar'
$metadata = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'dist/careerflow-image.json') | ConvertFrom-Json
if ($metadata.image -notmatch '^careerflow:[a-zA-Z0-9][a-zA-Z0-9_.-]*$' -or
    $metadata.imageId -notmatch '^sha256:[a-f0-9]{64}$' -or
    $metadata.sha256 -notmatch '^[a-f0-9]{64}$') { throw 'Invalid image metadata.' }
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $metadata.sha256) {
    throw 'Image archive checksum mismatch. Rebuild the Docker package.'
}
$sshOptions = @('-i', $IdentityFile, '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=8')
# Check permissions before uploading large files or stopping any service.
& ssh @sshOptions $Server "docker info >/dev/null && docker compose version && test -f '$RemoteDirectory/.env'"
if ($LASTEXITCODE -ne 0) { throw 'Server Docker is unavailable or permission denied; live service was not changed.' }
$uploads = @(
    @($archive, 'careerflow-image.tar.uploading'),
    @((Join-Path $PSScriptRoot 'docker-compose.server.yml'), 'docker-compose.server.next.yml'),
    @((Join-Path $PSScriptRoot 'deploy/docker/deploy.sh'), 'deploy-docker.next.sh')
)
foreach ($upload in $uploads) {
    & scp -q -i $IdentityFile -o IdentitiesOnly=yes $upload[0] "${Server}:$RemoteDirectory/$($upload[1])"
    if ($LASTEXITCODE -ne 0) { throw 'Docker deployment upload failed; live service was not changed.' }
}
& ssh @sshOptions $Server "cd '$RemoteDirectory' && bash deploy-docker.next.sh '$($metadata.image)' '$($metadata.imageId)' '$($metadata.sha256)'"
if ($LASTEXITCODE -ne 0) { throw 'Docker deployment failed. Review the rollback result above.' }
