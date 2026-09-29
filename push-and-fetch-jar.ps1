[CmdletBinding()]
param(
    [string]$Remote = 'origin',
    [string]$Branch = '',
    [string]$OutputPath = ''
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $OutputPath) {
    $OutputPath = Join-Path $PSScriptRoot 'local-artifacts\job-tracker.jar'
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath failed with exit code $LASTEXITCODE"
    }
}

function Get-CiRuns {
    param([string]$Commit)
    $json = & gh run list --workflow ci.yml --commit $Commit --limit 20 --json databaseId,status,conclusion,createdAt 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $json) { return @() }
    $parsed = $json | ConvertFrom-Json
    foreach ($run in $parsed) { Write-Output $run }
}

function Push-CurrentBranch {
    param([string]$RemoteName, [string]$BranchName)
    & git push $RemoteName $BranchName
    if ($LASTEXITCODE -eq 0) { return }

    Write-Host 'Configured Git proxy failed. Retrying with a direct connection...' -ForegroundColor Yellow
    Invoke-Checked git -c http.proxy= -c https.proxy= push $RemoteName $BranchName
}

function Test-GhAuthentication {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & gh auth status *> $null
        return $LASTEXITCODE -eq 0
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
}

Push-Location $PSScriptRoot
$tempDirectory = $null
try {
    # A stale GH_TOKEN can mask the valid credential stored by GitHub CLI.
    $authenticated = Test-GhAuthentication
    if (-not $authenticated -and (Test-Path Env:GH_TOKEN)) {
        Remove-Item Env:GH_TOKEN
        $authenticated = Test-GhAuthentication
    }
    if (-not $authenticated) {
        throw 'GitHub CLI is not authenticated. Run: gh auth login'
    }

    if (-not $Branch) {
        $Branch = (& git branch --show-current).Trim()
    }
    if (-not $Branch) {
        throw 'Detached HEAD detected. Specify a branch with -Branch.'
    }

    $commit = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $commit) {
        throw 'Unable to read the current commit.'
    }

    $knownRunIds = @{}
    foreach ($run in (Get-CiRuns -Commit $commit)) {
        $knownRunIds[[string]$run.databaseId] = $true
    }

    Write-Host "Pushing $Branch to $Remote..." -ForegroundColor Cyan
    Push-CurrentBranch -RemoteName $Remote -BranchName $Branch

    Write-Host 'Waiting for GitHub Actions to receive the commit...' -ForegroundColor Cyan
    $selectedRun = $null
    for ($attempt = 0; $attempt -lt 10 -and -not $selectedRun; $attempt++) {
        Start-Sleep -Seconds 3
        $selectedRun = Get-CiRuns -Commit $commit |
            Where-Object { $_ -and $_.PSObject.Properties['databaseId'] -and -not $knownRunIds.ContainsKey([string]$_.databaseId) } |
            Sort-Object createdAt -Descending |
            Select-Object -First 1
    }

    # A docs-only push may not match the workflow path filter. In that case,
    # dispatch the same workflow manually so this command always produces a JAR.
    if (-not $selectedRun) {
        Write-Host 'No automatic run detected. Dispatching CI manually...' -ForegroundColor Yellow
        Invoke-Checked gh workflow run ci.yml --ref $Branch
        for ($attempt = 0; $attempt -lt 20 -and -not $selectedRun; $attempt++) {
            Start-Sleep -Seconds 3
            $selectedRun = Get-CiRuns -Commit $commit |
                Where-Object { $_ -and $_.PSObject.Properties['databaseId'] -and -not $knownRunIds.ContainsKey([string]$_.databaseId) } |
                Sort-Object createdAt -Descending |
                Select-Object -First 1
        }
    }

    if (-not $selectedRun) {
        throw 'No CI run was found for this commit. Check the GitHub Actions page.'
    }

    $runId = [string]$selectedRun.databaseId
    Write-Host "Waiting for CI #$runId to complete..." -ForegroundColor Cyan
    Invoke-Checked gh run watch $runId --exit-status --interval 5

    $tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("job-tracker-artifact-" + [Guid]::NewGuid().ToString('N'))
    $downloadDirectory = Join-Path $tempDirectory 'download'
    $extractDirectory = Join-Path $tempDirectory 'extract'
    New-Item -ItemType Directory -Path $downloadDirectory, $extractDirectory -Force | Out-Null

    Write-Host 'Downloading and verifying the build artifact...' -ForegroundColor Cyan
    Invoke-Checked gh run download $runId --name job-tracker-linux-x64 --dir $downloadDirectory
    $archive = Join-Path $downloadDirectory 'job-tracker-linux-x64.tar.gz'
    $checksumFile = "$archive.sha256"
    if (-not (Test-Path -LiteralPath $archive) -or -not (Test-Path -LiteralPath $checksumFile)) {
        throw 'The Actions artifact is missing the archive or SHA-256 file.'
    }

    $expectedHash = ((Get-Content -LiteralPath $checksumFile -Raw).Trim() -split '\s+')[0].ToUpperInvariant()
    $actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($actualHash -ne $expectedHash) {
        throw 'The downloaded archive failed SHA-256 verification.'
    }

    Invoke-Checked tar -xzf $archive -C $extractDirectory
    $jar = Join-Path $extractDirectory 'job-tracker\job-tracker.jar'
    if (-not (Test-Path -LiteralPath $jar)) {
        throw 'job-tracker.jar was not found in the downloaded archive.'
    }

    $resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
    $outputDirectory = Split-Path -Parent $resolvedOutput
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $stagedOutput = "$resolvedOutput.new"
    Copy-Item -LiteralPath $jar -Destination $stagedOutput -Force
    Move-Item -LiteralPath $stagedOutput -Destination $resolvedOutput -Force

    $size = [Math]::Round((Get-Item -LiteralPath $resolvedOutput).Length / 1MB, 1)
    Write-Host "Done: $resolvedOutput ($size MB)" -ForegroundColor Green
    Write-Host 'Only the latest extracted JAR is retained locally; temporary downloads were removed.' -ForegroundColor Green
}
finally {
    Pop-Location
    if ($tempDirectory -and (Test-Path -LiteralPath $tempDirectory)) {
        $resolvedTemp = [System.IO.Path]::GetFullPath($tempDirectory)
        $systemTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        if ($resolvedTemp.StartsWith($systemTemp, [System.StringComparison]::OrdinalIgnoreCase)) {
            [System.IO.Directory]::Delete($resolvedTemp, $true)
        }
    }
}
