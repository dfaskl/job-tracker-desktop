[CmdletBinding()]
param(
    [switch]$Deploy
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$outputDirectory = Join-Path $repoRoot 'dist'
$sourceJar = Join-Path $repoRoot 'backend\target\job-tracker.jar'
$outputJar = Join-Path $outputDirectory 'job-tracker.jar'

Push-Location $repoRoot
try {
    $jdk21 = 'C:\Program Files\Java\jdk-21'
    if (Test-Path (Join-Path $jdk21 'bin\java.exe')) {
        $env:JAVA_HOME = $jdk21
        $env:Path = "$jdk21\bin;$env:Path"
    }

    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw '未找到 Maven。请先安装 Maven 3.9，并确认 mvn 已加入 PATH。'
    }
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        throw '未找到 Node.js/npm。请先安装 Node.js 22，并确认 npm 已加入 PATH。'
    }

    $javaVersion = (& java -version 2>&1 | Select-Object -First 1) -join ''
    if ($javaVersion -notmatch 'version "21(?:\.|\")') {
        throw "当前不是 Java 21：$javaVersion"
    }

    Write-Host '正在构建前端、运行测试并打包 JAR……' -ForegroundColor Cyan
    & mvn -B -f backend/pom.xml clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven 构建失败，退出码：$LASTEXITCODE"
    }
    if (-not (Test-Path $sourceJar)) {
        throw "构建完成但没有找到：$sourceJar"
    }

    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    Copy-Item -Force $sourceJar $outputJar
    $jar = Get-Item $outputJar
    $sizeMb = [Math]::Round($jar.Length / 1MB, 1)

    Write-Host ''
    Write-Host '本地 JAR 构建完成。' -ForegroundColor Green
    Write-Host "文件：$($jar.FullName)"
    Write-Host "大小：$sizeMb MB"
    if ($Deploy) {
        Write-Host ''
        & (Join-Path $repoRoot 'deploy-server.ps1') -JarPath $outputJar
        if ($LASTEXITCODE -ne 0) {
            throw "服务器部署失败，退出码：$LASTEXITCODE"
        }
    }
    else {
        Write-Host '需要部署时运行：.\build-jar.ps1 -Deploy'
    }
}
finally {
    Pop-Location
}
