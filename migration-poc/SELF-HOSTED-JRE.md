# 自带 Java 21 的服务器部署包

部署包面向 Linux x64 服务器，不要求服务器安装 Docker、Java、Maven或 Node。解压后的 Java 运行时、应用、配置、日志和 PID 文件都位于同一个目录。

## 1. 生成部署包

推送到 `codex/migration-poc-demo` 后，GitHub Actions 的 `CI` 工作流会自动构建并上传名为 `job-tracker-linux-x64` 的产物。也可以在 GitHub 仓库的 **Actions → CI → Run workflow** 中手动选择分支运行。

构建完成后下载产物 ZIP，解压可得到：

```text
job-tracker-linux-x64.tar.gz
job-tracker-linux-x64.tar.gz.sha256
```

Windows PowerShell 可校验压缩包：

```powershell
(Get-FileHash .\job-tracker-linux-x64.tar.gz -Algorithm SHA256).Hash.ToLower()
Get-Content .\job-tracker-linux-x64.tar.gz.sha256
```

两个哈希值应一致。

## 2. 上传到服务器

以下以服务器目录 `/home/zhoujiajun/jobtracker` 为例。如果已经创建了其他专用目录，请替换为实际路径。

在 Windows PowerShell 上传：

```powershell
scp .\job-tracker-linux-x64.tar.gz zhoujiajun@服务器地址:/home/zhoujiajun/jobtracker/
```

登录服务器并解压到专用目录：

```bash
cd /home/zhoujiajun/jobtracker
tar -xzf job-tracker-linux-x64.tar.gz --strip-components=1
chmod +x start.sh stop.sh status.sh migrate-to-sqlite.sh
```

解压完成后的目录结构：

```text
jobtracker/
├── runtime/                 # 内置 Java 21 JRE
├── job-tracker.jar          # 前端与后端应用
├── env.example              # 配置模板
├── start.sh
├── stop.sh
├── status.sh
├── migrate-to-sqlite.sh    # 一次性迁移 Neon 数据并安全切换配置
├── data/                    # SQLite 主数据库
├── backups/                 # SQLite 与迁移前配置备份
├── logs/                    # 首次启动时创建
└── run/                     # 首次启动时创建
```

## 3. 配置应用

```bash
cd /home/zhoujiajun/jobtracker
cp env.example .env
chmod 600 .env
nano .env
```

首次从旧版本升级时，先保留当前 Neon 数据库连接，确认新版程序可以启动，再使用部署包内的迁移脚本切换到 SQLite。迁移已有数据时，`SESSION_SECRET` 和 `ENCRYPTION_KEY` 必须沿用原值，尤其不能随意更换 `ENCRYPTION_KEY`。

服务器提供的端口快照中 `18080` 未被占用，因此模板默认使用它。启动前可再次确认：

```bash
ss -H -ltn 'sport = :18080'
```

无输出代表端口空闲。

## 4. 启停和检查

启动：

```bash
cd /home/zhoujiajun/jobtracker
./start.sh
```

检查：

```bash
./status.sh
curl http://127.0.0.1:18080/healthz
tail -n 100 logs/app.log
```

停止：

```bash
./stop.sh
```

如果服务器内网防火墙允许，可尝试从同一内网访问 `http://10.12.144.7:18080`。正式域名和 HTTPS 仍需服务器管理员把现有反向代理转发到 `127.0.0.1:18080`。

## 5. 更新版本

先停止应用并备份旧 JAR：

```bash
cd /home/zhoujiajun/jobtracker
./stop.sh
cp job-tracker.jar "job-tracker.jar.$(date +%Y%m%d-%H%M%S).bak"
```

将新压缩包上传到同一目录，再覆盖解压：

```bash
tar -xzf job-tracker-linux-x64.tar.gz --strip-components=1
./start.sh
```

部署包不会包含或覆盖 `.env`、`logs/` 和 `run/`。

## 6. 从 Neon 一次性迁移到 SQLite

迁移前请先确认新版部署包已经完整解压，且 `.env` 仍指向原来的 Neon PostgreSQL。然后执行：

```bash
cd /home/zhoujiajun/jobtracker
./migrate-to-sqlite.sh
```

脚本会自动停止应用，直接通过 JDBC 从 Neon 读取全部业务表，在 `data/jobtracker.db` 中建立 SQLite 数据库，逐表核对记录数，再修改 `.env` 并重启。迁移失败时不会改动原数据库，也会恢复原配置并尝试重新启动。

SQLite 数据每天 03:30 自动备份到 `backups/`，默认保留最近 14 份。数据库文件和备份目录权限均限制为当前服务器用户访问。
