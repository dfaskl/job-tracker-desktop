param(
    [string]$DemoBaseUrl = "https://job-tracker-migration-poc.onrender.com",
    [string]$LegacyBaseUrl = "https://job-tracker-web-c8b0.onrender.com"
)

$ErrorActionPreference = "Stop"
$checks = @(
    @{ Name = "旧服务健康"; Url = "$LegacyBaseUrl/healthz" },
    @{ Name = "迁移服务健康"; Url = "$DemoBaseUrl/healthz" },
    @{ Name = "运行与兼容状态"; Url = "$DemoBaseUrl/api/poc/status" },
    @{ Name = "职位写入沙箱"; Url = "$DemoBaseUrl/api/poc/application-sandbox/status" },
    @{ Name = "日程写入沙箱"; Url = "$DemoBaseUrl/api/poc/event-sandbox/status" },
    @{ Name = "备份恢复沙箱"; Url = "$DemoBaseUrl/api/poc/backup-sandbox/status" },
    @{ Name = "会话模式"; Url = "$DemoBaseUrl/api/poc/session-mode" },
    @{ Name = "AI 邮件沙箱"; Url = "$DemoBaseUrl/api/poc/ai-sandbox/status" },
    @{ Name = "管理员沙箱"; Url = "$DemoBaseUrl/api/poc/admin-sandbox/status" }
)

$results = foreach ($check in $checks) {
    try {
        $response = Invoke-WebRequest -Uri $check.Url -TimeoutSec 30 -SkipHttpErrorCheck
        [pscustomobject]@{
            Check = $check.Name
            Http = [int]$response.StatusCode
            Passed = [int]$response.StatusCode -eq 200
        }
    } catch {
        [pscustomobject]@{
            Check = $check.Name
            Http = 0
            Passed = $false
        }
    }
}

$results | Format-Table -AutoSize
$failed = @($results | Where-Object { -not $_.Passed })
if ($failed.Count -gt 0) {
    throw "部署验收失败：$($failed.Count) 项检查未通过"
}

Write-Output "部署只读验收通过：旧服务与迁移 Demo 均正常，全部公开状态接口可用。"
