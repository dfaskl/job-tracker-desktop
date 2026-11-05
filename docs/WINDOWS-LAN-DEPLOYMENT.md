# Windows 局域网部署

运行目录为 `D:\CareerFlow`，与源代码、旧服务器备份分开保存。

- `job-tracker.jar`：当前正在使用的版本。
- `data/jobtracker.db`：正式 SQLite 数据，运行时的 `-wal`、`-shm` 文件也属于数据库，不能单独删除或用普通复制方式做在线备份。
- `environment.json`：从服务器迁移的加密密钥、会话配置等，包含秘密，不提交 Git。
- `deployment.json`：Windows Java 路径。
- `logs/app.log`、`logs/error.log`：运行日志。
- `backups/`：每小时生成的一致性数据库备份，只保留最新一份；以及当前版本的 JAR。应额外在其他磁盘保留重要备份，避免同一磁盘损坏导致数据和备份一起丢失。

Windows 任务 `CareerFlow Local Service` 通过无窗口启动器 `start-hidden.vbs` 在当前用户登录后于后台启动，并在 Java 进程退出后尝试恢复；无需保留终端、Codex 或 VS Code 窗口。电脑必须开机、保持登录、不休眠。没有保存 Windows 密码。网络防火墙规则仅允许本机所在子网访问 TCP 18080。

启动与停止：

```powershell
Start-ScheduledTask -TaskName 'CareerFlow Local Service'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\CareerFlow\stop.ps1
```

从源码仓库运行 `deploy-local.ps1` 更新 JAR，数据库和配置不覆盖。升级失败会恢复旧 JAR。迁移后的本机 `local-deployment.json` 会将既有 `build-jar.ps1 -Deploy` / `deploy-server.ps1` 默认重定向到本地部署。该文件被 Git 忽略；显式传入 `deploy-server.ps1 -Server ...` 才会重新向服务器部署。

原任务 `CareerFlow Hourly Local Backup` 应停用；`server-backup/current` 保留最后的服务器迁移快照，不再用远端数据覆盖本地正式数据。

迁移顺序：先备份并准备本地运行环境；暂停旧服务及自动启动来源，再生成最后一份一致性快照；复制数据库与配置后启动本地，核对数据库内容和登录功能；失败时停用本地并恢复旧服务。切勿同时运行两份正式服务。

新 IP 下需要重新登录网站。原密码与账号保留；MCP 等客户端的服务 URL 也需要改为本地电脑的局域网地址。局域网 IP 可能由 DHCP 改变，建议在路由器为电脑配置地址保留。
