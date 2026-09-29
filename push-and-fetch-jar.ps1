[CmdletBinding()]
param(
    [string]$Remote = 'origin',
    [string]$Branch = '',
    [string]$Commit = '',
    [switch]$FetchOnly,
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

    if (-not $Commit) {
        $Commit = (& git rev-parse HEAD).Trim()
    }
    if ($LASTEXITCODE -ne 0 -or -not $Commit) {
        throw 'Unable to read the current commit.'
    }

    $knownRunIds = @{}
    if (-not $FetchOnly) {
        foreach ($run in (Get-CiRuns -Commit $Commit)) {
            $knownRunIds[[string]$run.databaseId] = $true
        }

        Write-Host "Pushing $Branch to $Remote..." -ForegroundColor Cyan
        $previousSkipHook = $env:JOB_TRACKER_SKIP_AUTO_FETCH
        $env:JOB_TRACKER_SKIP_AUTO_FETCH = '1'
        try {
            Push-CurrentBranch -RemoteName $Remote -BranchName $Branch
        }
        finally {
            $env:JOB_TRACKER_SKIP_AUTO_FETCH = $previousSkipHook
        }
    }
    else {
        Write-Host "Waiting for $Commit to reach $Remote/$Branch..." -ForegroundColor Cyan
        $remoteRef = "refs/heads/$Branch"
        $remoteReady = $false
        for ($attempt = 0; $attempt -lt 60 -and -not $remoteReady; $attempt++) {
            $line = & git -c http.proxy= -c https.proxy= ls-remote $Remote $remoteRef 2>$null
            $remoteReady = $LASTEXITCODE -eq 0 -and $line -and (($line -split '\s+')[0] -eq $Commit)
            if (-not $remoteReady) { Start-Sleep -Seconds 2 }
        }
        if (-not $remoteReady) { throw 'The pushed commit did not appear on the remote in time.' }
    }

    Write-Host 'Waiting for GitHub Actions to receive the commit...' -ForegroundColor Cyan
    $selectedRun = $null
    for ($attempt = 0; $attempt -lt 10 -and -not $selectedRun; $attempt++) {
        Start-Sleep -Seconds 3
        $selectedRun = Get-CiRuns -Commit $Commit |
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
            $selectedRun = Get-CiRuns -Commit $Commit |
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
    New-Item -ItemType Directory -Path $downloadDirectory -Force | Out-Null

    Write-Host 'Downloading the server JAR artifact...' -ForegroundColor Cyan
    $artifactJson = & gh api "repos/dfaskl/job-tracker-desktop/actions/runs/$runId/artifacts"
    if ($LASTEXITCODE -ne 0 -or -not $artifactJson) { throw 'Unable to list artifacts for the CI run.' }
    $artifactResponse = $artifactJson | ConvertFrom-Json
    $artifact = @($artifactResponse.artifacts) |
        Where-Object { $_.name -eq 'job-tracker-jar' } |
        Select-Object -First 1
    if (-not $artifact) { throw 'The job-tracker-jar artifact was not found.' }

    $artifactZip = Join-Path $tempDirectory 'job-tracker-jar.zip'
    $token = (& gh auth token).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $token) { throw 'Unable to read the GitHub CLI token.' }
    Invoke-Checked curl.exe --fail --location --retry 3 --silent --show-error `
        --header "Authorization: Bearer $token" `
        --header 'Accept: application/vnd.github+json' `
        --header 'X-GitHub-Api-Version: 2022-11-28' `
        --output $artifactZip ([string]$artifact.archive_download_url)
    Expand-Archive -LiteralPath $artifactZip -DestinationPath $downloadDirectory -Force
    $jar = Join-Path $downloadDirectory 'job-tracker.jar'
    if (-not (Test-Path -LiteralPath $jar)) {
        throw 'job-tracker.jar was not found in the downloaded artifact.'
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
