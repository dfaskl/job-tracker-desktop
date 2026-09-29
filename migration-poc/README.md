# CareerFlow 应用

此目录包含 CareerFlow 正式版应用。Vue 前端由 Maven 调用 Vite 构建，并作为静态资源打入 Spring Boot JAR；部署时只需运行一个 Java 进程。

## 运行模式

### SQLite 自托管（推荐）

适合单台服务器和当前生产环境：

```text
APP_DATABASE_URL=jdbc:sqlite:./data/jobtracker.db
```

SQLite 文件应放在部署目录的 `data/` 下，并与 JAR 分开备份。应用支持定时一致性备份，默认每天 03:30 执行并保留最近14份。

### PostgreSQL / Docker Compose

需要多人高并发或已有 PostgreSQL 时，可使用本目录的 `docker-compose.yml`：

```sh
docker compose up -d --build
```

数据库由具名卷 `jobtracker_data` 持久化。升级前应先导出数据库或备份数据卷。

## 必需配置

复制 `.env.example` 或 `deploy/self-hosted/env.example`，至少配置：

- `APP_DATABASE_URL`：SQLite JDBC 地址或 PostgreSQL 连接地址
- `SESSION_SECRET`：会话签名密钥，至少32个字符
- `ENCRYPTION_KEY`：用户 AI API Key 的服务端加密密钥，至少32个字符
- `ADMIN_EMAIL`：管理员账号邮箱
- `APP_PORT`：监听端口，自托管示例使用 `18080`

常用可选配置：

- `ALLOW_REGISTRATION`：是否允许新用户注册
- `REGISTRATION_CODE`：新用户注册邀请码，留空表示无需邀请码
- `SESSION_DAYS`：登录会话有效天数
- `AI_CALLS_ENABLED`：是否允许调用用户配置的 AI 服务
- `AI_ALLOWED_HOSTS`：允许访问的 AI API 域名列表
- `SQLITE_BACKUP_ENABLED`：是否启用 SQLite 自动备份
- `SQLITE_BACKUP_RETENTION`：SQLite 备份保留数量

不要把真实密钥、邮箱授权码或生产 `.env` 提交到仓库。

## 本地构建

要求：Java 21、Maven 3.9、Node.js 22。

在仓库根目录运行：

```powershell
mvn -f migration-poc/backend/pom.xml clean package
```

构建过程会依次完成：

1. `npm ci`
2. Vue / TypeScript 生产构建
3. Spring Boot 编译与测试
4. 前端资源复制到 `classpath:/static`
5. 生成 `backend/target/job-tracker.jar`

运行：

```powershell
java -jar migration-poc/backend/target/job-tracker.jar
```

## GitHub Actions 发布包

推送 `main` 或 `codex/migration-poc-demo` 后，`.github/workflows/ci.yml` 会自动测试并生成：

```text
job-tracker-linux-x64.tar.gz
job-tracker-linux-x64.tar.gz.sha256
```

压缩包包含应用 JAR、Java 21 JRE、环境变量示例和启停脚本。下载后无需服务器安装 Java 21。

完整部署、更新和回滚步骤见 [SELF-HOSTED-JRE.md](SELF-HOSTED-JRE.md)。

## 服务器操作

```sh
./start.sh
./status.sh
./stop.sh
tail -f logs/app.log
curl http://127.0.0.1:18080/healthz
```

更新程序时保留以下内容：

```text
.env
data/
backups/
logs/
```

不要再次执行 `migrate-to-sqlite.sh`；它只用于首次从 PostgreSQL 迁移现有数据。
