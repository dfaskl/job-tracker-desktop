# 本机最新部署备份

> 已改为 Windows 本地正式部署时，以下服务器拉取任务停用；`server-backup/current` 保留迁移前最后快照。当前数据库与每小时备份见 [Windows 局域网部署](WINDOWS-LAN-DEPLOYMENT.md)。不要重新启用旧服务器任务来覆盖正式数据。

本机备份目录为 `server-backup/current/`，整个 `server-backup/` 被 Git 忽略。目录权限限制为当前 Windows 用户、SYSTEM 和本机管理员；其中 `.env` 包含恢复加密配置所需的密钥，不能提交 Git 或公开分享。

## 备份内容

- `job-tracker.jar`：从服务器下载并核对哈希的实际部署版本。
- `data/jobtracker.db`：SQLite 一致性快照，包含账户、投递、日程、会话、MCP 令牌、站内配置和业务数据。
- `.env`：服务器环境配置，保留会话密钥、加密密钥等恢复必需参数。
- `runtime.tar.gz`：服务器 Java 运行时，单独归档以保留 Linux 权限和链接。
- `start.sh`、`stop.sh`、`status.sh`，以及存在时的环境模板、迁移脚本与当前应用日志。
- `manifest.json`：文件 SHA256 校验值和快照时间；小时同步后会更新数据库及配置文件的校验值。

只保留 `current` 一份最新副本。不会把服务器 `backups/` 的历史版本再次下载。下载和校验先在临时目录完成，成功后覆盖当前副本；全量替换时短暂使用 `.previous` 以便恢复，成功后立即删除。不会复制正在写入的数据库文件或 WAL 文件；通过 SQLite backup API 生成独立快照并执行 `PRAGMA quick_check`。

## 自动更新

1. 每次 `deploy-server.ps1` 部署成功后，自动进行一次全量本机备份；即使服务器已是相同 JAR，也会核对并刷新本机备份。失败回滚不会把失败的新版本保存成本机最新版本。
2. Windows 计划任务 **CareerFlow Hourly Local Backup** 每小时运行一次，同时在当前用户登录时补跑。正常小时同步仅下载数据库和 `.env`；若发现服务器 JAR 与本地不同，会自动升级为全量同步。
3. 计划任务使用 Windows PowerShell，以当前用户的普通权限隐藏运行；无需保持 Codex 或 VS Code 打开。
4. 电脑需开机、用户已登录，并能通过 SSH 访问服务器。关机或网络不可达期间无法备份；任务配置为恢复可运行时补跑，失败后每 10 分钟重试，最多 3 次。不会在电脑关机时承诺一小时备份一次。
5. 任务与手动备份互斥；服务器端快照与 JAR 替换共用锁，避免读取到正在部署中的 JAR。

本机 SSH 密钥仍使用 `~/.ssh/jobtracker_server`，不会把它复制进备份，也不会把密码写进计划任务。

## 手动运行与检查

```powershell
# 全量刷新最新副本
.\backup-server.ps1

# 只刷新数据库和配置；发现缺少完整副本或 JAR 不一致时自动执行全量
.\backup-server.ps1 -DatabaseOnly

# 重新注册每小时任务
.\install-backup-task.ps1

# 查看任务最近结果；LastTaskResult 为 0 表示执行成功
Get-ScheduledTaskInfo -TaskName 'CareerFlow Hourly Local Backup'

# 查看最新成功备份时间与日志
Get-Content .\server-backup\status.json
Get-Content .\server-backup\backup.log -Tail 10
```

`backup.log` 大小受限，不保存轮换日志历史。脚本不会在日志里输出 `.env` 内容。`status.json` 记录最新全量、最新数据库备份时间和对应 JAR 哈希。发生网络、校验或 SQLite 快照错误时任务会失败并记录错误，已经验证的本地备份保留。

如果移动了项目目录、修改了服务器 IP 或更换 SSH 密钥，需要更新脚本参数和重新注册任务。

## 恢复

先停止目标服务器上的旧应用，再把 `current/` 内文件复制到新的部署目录。Linux 上在该目录解压 `runtime.tar.gz`（解压后生成 `runtime/`），并执行：

```bash
tar -xzf runtime.tar.gz
chmod 700 data
chmod 600 .env
chmod +x start.sh stop.sh status.sh
./start.sh
```

如果换了部署目录，应把 `.env` 中 `APP_DATABASE_URL` 改成新目录的 `data/jobtracker.db` 路径；`SESSION_SECRET`、`ENCRYPTION_KEY` 应保持备份中的值。不要在应用运行时覆盖数据库。备份的 Java 运行时用于 Linux x64；在 Windows 本地启动 JAR 时应使用本机 Java 21。
