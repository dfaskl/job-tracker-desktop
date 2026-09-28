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
chmod +x start.sh stop.sh status.sh
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

第一阶段建议继续填写当前 Neon 的数据库连接地址，先确认自托管应用运行正常。迁移已有数据时，`SESSION_SECRET` 和 `ENCRYPTION_KEY` 应沿用 Render 中的原值，尤其不能随意更换 `ENCRYPTION_KEY`。

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

