[CmdletBinding()]
param(
    [string]$Server = 'zhoujiajun@10.12.152.159',
    [string]$RemoteDirectory = '/home/zhoujiajun/jobTracker',
    [string]$IdentityFile = (Join-Path $env:USERPROFILE '.ssh\jobtracker_server'),
    [string]$BackupDirectory = '',
    [switch]$DatabaseOnly
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($BackupDirectory)) { $BackupDirectory = Join-Path $PSScriptRoot 'server-backup' }
if ($Server -notmatch '^[A-Za-z0-9._-]+(@[A-Za-z0-9.-]+)?$' -or
    $RemoteDirectory -notmatch '^/[A-Za-z0-9._/-]+$') { throw 'Invalid backup server or directory.' }
$BackupDirectory = [IO.Path]::GetFullPath($BackupDirectory)
New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
# The backup contains .env and account data. Do not inherit broad workspace permissions.
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $BackupDirectory /inheritance:r /grant:r "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Unable to protect the local backup directory.' }
$mutex = New-Object Threading.Mutex($false, 'Local\CareerFlowServerBackup')
$held = $false
$stage = $null
$token = $null
$sshOptions = @('-i', $IdentityFile, '-o', 'IdentitiesOnly=yes', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=8', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=3')
$current = Join-Path $BackupDirectory 'current'
function Write-BackupLog([string]$Message) {
    $log = Join-Path $BackupDirectory 'backup.log'
    if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).Length -gt 65536) { Clear-Content -LiteralPath $log }
    ('{0:o} {1}' -f (Get-Date), $Message) | Add-Content -LiteralPath $log -Encoding UTF8
    Write-Host $Message
}
function Remove-StagingDirectory([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (-not $resolved.StartsWith($BackupDirectory + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path $resolved -Leaf) -notmatch '^(\.incoming-[a-f0-9]{32}|\.previous)$') { throw 'Unsafe backup cleanup path.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
try {
    try { $held = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $held = $true }
    if (-not $held) { Write-Host 'A backup is already running; skipped.'; return }
    $helper = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'scripts\server-backup.py')
    $kind = if ($DatabaseOnly -and (Test-Path -LiteralPath (Join-Path $current 'job-tracker.jar'))) { 'database' } else { 'full' }
    for ($attempt=0; $attempt -lt 2; $attempt++) {
        $token = [Guid]::NewGuid().ToString('N')
        $stage = Join-Path $BackupDirectory ('.incoming-' + $token)
        New-Item -ItemType Directory -Path $stage | Out-Null
        Write-BackupLog "Creating $kind snapshot from $Server."
        $raw = $helper | & ssh @sshOptions $Server "python3 - --root '$RemoteDirectory' --token '$token' --kind '$kind'"
        if ($LASTEXITCODE -ne 0) { throw 'Server snapshot failed; the previous backup is retained.' }
        $metadata = ($raw -join "`n") | ConvertFrom-Json
        $archive = Join-Path $stage 'snapshot.tar.gz'
        & scp -q -i $IdentityFile -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=8 "${Server}:$RemoteDirectory/run/local-backup-$token/snapshot.tar.gz" $archive
        if ($LASTEXITCODE -ne 0) { throw 'Snapshot download failed; the previous backup is retained.' }
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $metadata.sha256) {
            throw 'Downloaded archive checksum failed; the previous backup is retained.'
        }
        $entries = @(& tar.exe -tzf $archive)
        if ($LASTEXITCODE -ne 0) { throw 'Invalid snapshot archive.' }
        $allowed = @('data/jobtracker.db','.env','job-tracker.jar','start.sh','stop.sh','status.sh','env.example','migrate-to-sqlite.sh','logs/app.log','runtime.tar.gz','manifest.json')
        if ($entries | Where-Object { $_ -notin $allowed }) { throw 'Unexpected file in snapshot archive.' }
        & tar.exe -xzf $archive -C $stage
        if ($LASTEXITCODE -ne 0) { throw 'Snapshot extraction failed.' }
        Remove-Item -LiteralPath $archive
        $manifest = Get-Content -Raw -LiteralPath (Join-Path $stage 'manifest.json') | ConvertFrom-Json
        if ($manifest.kind -ne $kind) { throw 'Snapshot kind mismatch.' }
        if ($kind -eq 'full' -and $manifest.jarSha256 -ne $manifest.files.'job-tracker.jar') {
            throw 'The server JAR changed during snapshot creation; retry the backup.'
        }
        $required = @('data/jobtracker.db','.env')
        if ($kind -eq 'full') { $required += @('job-tracker.jar','runtime.tar.gz','start.sh','stop.sh','status.sh') }
        foreach ($name in $required) {
            if (-not $manifest.files.PSObject.Properties[$name]) { throw 'Required recovery file missing from manifest.' }
        }
        foreach ($file in $manifest.files.PSObject.Properties) {
            if ($file.Name -notin $allowed) { throw 'Unexpected manifest path.' }
            if ((Get-FileHash -LiteralPath (Join-Path $stage $file.Name) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.Value) {
                throw 'Snapshot file checksum failed; the previous backup is retained.'
            }
        }
        $helper | & ssh @sshOptions $Server "python3 - --root '$RemoteDirectory' --token '$token' --kind cleanup" | Out-Null
        $token = $null
        if ($kind -eq 'database' -and (Get-FileHash -LiteralPath (Join-Path $current 'job-tracker.jar') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.jarSha256) {
            Remove-StagingDirectory $stage
            $stage = $null
            $kind = 'full'
            continue
        }
        if ($kind -eq 'full') {
            $previous = Join-Path $BackupDirectory '.previous'
            if (Test-Path -LiteralPath $previous) { throw 'An interrupted replacement exists; review server-backup/.previous before retrying.' }
            if (Test-Path -LiteralPath $current) { Move-Item -LiteralPath $current -Destination $previous }
            try { Move-Item -LiteralPath $stage -Destination $current; $stage = $null }
            catch {
                if (Test-Path -LiteralPath $previous) { Move-Item -LiteralPath $previous -Destination $current }
                throw
            }
            if (Test-Path -LiteralPath $previous) { Remove-StagingDirectory $previous }
            $status = @{lastFullBackupAt=$manifest.createdAt; lastDatabaseBackupAt=$manifest.createdAt; jarSha256=$manifest.jarSha256}
        } else {
            # Individual replacements are atomic; the verified database is never copied in place.
            foreach ($name in @('data/jobtracker.db','.env')) {
                [IO.File]::Replace((Join-Path $stage $name), (Join-Path $current $name), [NullString]::Value)
            }
            $status = Get-Content -Raw -LiteralPath (Join-Path $BackupDirectory 'status.json') | ConvertFrom-Json
            $status.lastDatabaseBackupAt = $manifest.createdAt
            $fullManifest = Get-Content -Raw -LiteralPath (Join-Path $current 'manifest.json') | ConvertFrom-Json
            foreach ($name in @('data/jobtracker.db','.env')) {
                $fullManifest.files.PSObject.Properties[$name].Value = $manifest.files.PSObject.Properties[$name].Value
            }
            $fullManifest | Add-Member -NotePropertyName lastDatabaseBackupAt -NotePropertyValue $manifest.createdAt -Force
            $manifestNext = Join-Path $stage 'manifest.next.json'
            $fullManifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestNext -Encoding UTF8
            [IO.File]::Replace($manifestNext, (Join-Path $current 'manifest.json'), [NullString]::Value)
        }
        $status | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $BackupDirectory 'status.json') -Encoding UTF8
        Write-BackupLog "Backup verified: $current"
        break
    }
} catch {
    Write-BackupLog ('FAILED: ' + $_.Exception.Message)
    throw
} finally {
    if ($token -and $helper) {
        $helper | & ssh @sshOptions $Server "python3 - --root '$RemoteDirectory' --token '$token' --kind cleanup" | Out-Null
    }
    if ($stage -and (Test-Path -LiteralPath $stage)) {
        $resolved = [IO.Path]::GetFullPath($stage)
        if ($resolved.StartsWith($BackupDirectory + [IO.Path]::DirectorySeparatorChar) -and (Split-Path $resolved -Leaf) -match '^\.incoming-[a-f0-9]{32}$') {
            Remove-StagingDirectory $resolved
        }
    }
    if ($held) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
