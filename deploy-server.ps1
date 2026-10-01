[CmdletBinding()]
param(
    [string]$Server = 'jobtracker-server',
    [string]$RemoteDirectory = '/home/zhoujiajun/jobTracker',
    [string]$JarPath = (Join-Path $PSScriptRoot 'dist\job-tracker.jar'),
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

if ($Server -notmatch '^[A-Za-z0-9._-]+$') {
    throw '服务器名称包含不受支持的字符。'
}
if ($RemoteDirectory -notmatch '^/[A-Za-z0-9._/-]+$') {
    throw '服务器目录包含不受支持的字符。'
}
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw "没有找到待部署文件：$JarPath"
}
if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    throw '未找到 ssh 命令。'
}
if (-not (Get-Command scp -ErrorAction SilentlyContinue)) {
    throw '未找到 scp 命令。'
}

$jar = Get-Item -LiteralPath $JarPath
$localHash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
$remoteUpload = "$RemoteDirectory/job-tracker.jar.uploading"

Write-Host "正在检查服务器：$Server" -ForegroundColor Cyan
& ssh -o BatchMode=yes -o ConnectTimeout=8 $Server "test -d '$RemoteDirectory' && test -x '$RemoteDirectory/start.sh' && test -x '$RemoteDirectory/stop.sh' && test -x '$RemoteDirectory/status.sh'"
if ($LASTEXITCODE -ne 0) {
    throw '服务器目录或启停脚本检查失败。'
}

$remoteHash = (& ssh -o BatchMode=yes $Server "sha256sum '$RemoteDirectory/job-tracker.jar' | cut -d' ' -f1").Trim().ToLowerInvariant()
if ($LASTEXITCODE -ne 0) {
    throw '读取服务器 JAR 校验值失败。'
}
if (-not $Force -and $remoteHash -eq $localHash) {
    Write-Host '服务器已经是相同版本，无需上传或重启。' -ForegroundColor Green
    return
}

Write-Host "正在上传：$($jar.Name)" -ForegroundColor Cyan
& scp -q $jar.FullName "${Server}:$remoteUpload"
if ($LASTEXITCODE -ne 0) {
    throw "JAR 上传失败，退出码：$LASTEXITCODE"
}

$remoteScript = @'
set -u
remote_dir='__REMOTE_DIRECTORY__'
expected_hash='__EXPECTED_HASH__'
cd "$remote_dir" || exit 20

actual_hash="$(sha256sum job-tracker.jar.uploading | cut -d' ' -f1)"
if [ "$actual_hash" != "$expected_hash" ]; then
  rm -f job-tracker.jar.uploading
  echo "上传文件校验失败" >&2
  exit 21
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="$remote_dir/backups/deployments"
backup_jar="$backup_dir/job-tracker-$timestamp.jar"
mkdir -p "$backup_dir"
cp -p job-tracker.jar "$backup_jar" || exit 22

./stop.sh || exit 23
mv -f job-tracker.jar.uploading job-tracker.jar || exit 24
chmod 0644 job-tracker.jar

rollback() {
  echo "新版本启动失败，正在恢复旧 JAR……" >&2
  ./stop.sh >/dev/null 2>&1 || true
  cp -p "$backup_jar" job-tracker.jar
  ./start.sh || true
  tail -n 80 logs/app.log 2>/dev/null || true
  exit 25
}

./start.sh || rollback
healthy=0
for attempt in $(seq 1 30); do
  if curl -fsS --connect-timeout 2 http://127.0.0.1:18080/healthz >/dev/null; then
    healthy=1
    break
  fi
  sleep 1
done
if [ "$healthy" -ne 1 ]; then
  rollback
fi

./status.sh
printf 'DEPLOYED_SHA256='
sha256sum job-tracker.jar | cut -d' ' -f1
echo "BACKUP_JAR=$backup_jar"
'@
$remoteScript = $remoteScript.Replace('__REMOTE_DIRECTORY__', $RemoteDirectory).Replace('__EXPECTED_HASH__', $localHash)

Write-Host '正在重启并检查健康状态……' -ForegroundColor Cyan
$remoteScript | & ssh -o BatchMode=yes $Server 'bash -s'
if ($LASTEXITCODE -ne 0) {
    throw "服务器部署失败，退出码：$LASTEXITCODE"
}

Write-Host '服务器部署完成，健康检查已通过。' -ForegroundColor Green
