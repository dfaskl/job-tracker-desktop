# 求职进度本（Vue + Java）

这是正式版单体部署目录：Vue 前端会打包进 Spring Boot，应用与 PostgreSQL 可通过 Docker Compose 一起运行。

## 自有服务器快速部署

服务器需要安装 Docker 与 Docker Compose，并开放一个 HTTP 端口。进入本目录后执行：

```sh
chmod +x scripts/setup-self-host.sh
./scripts/setup-self-host.sh your-admin@example.com
```

Windows 主机可执行：

```powershell
.\scripts\setup-self-host.ps1 -AdminEmail your-admin@example.com
```

脚本只要求管理员邮箱，其余必要密钥会随机生成并写入本机 `.env`（该文件已被 Git 忽略），随后构建并启动应用。默认访问地址为 `http://服务器地址:8080`。

数据库使用具名卷 `jobtracker_data` 持久化。应用启动时会自动创建缺失的数据表，因此首次部署不需要手动执行 SQL。升级前仍建议先导出业务数据或备份数据库卷。

## 最小正式配置

直接运行 JAR 或使用已有 PostgreSQL 时，核心变量如下：

- `APP_DATABASE_URL`：唯一的 PostgreSQL 连接地址。
- `SESSION_SECRET`：会话签名密钥，至少 32 个字符。
- `ENCRYPTION_KEY`：用户 AI API Key 的服务端加密主密钥，至少 32 个字符。
- `ADMIN_EMAIL`：管理员账号邮箱；该用户注册或登录后会获得管理员权限。

常用可选变量：

- `ALLOW_REGISTRATION`：是否允许注册，默认 `true`，管理员页面可继续调整。
- `REGISTRATION_CODE`：留空表示注册无需邀请码。
- `SESSION_DAYS`：登录有效期，默认 7 天，范围 1–30 天。
- `AI_CALLS_ENABLED`：是否允许调用用户配置的 AI 接口，默认 `true`。
- `AI_ALLOWED_HOSTS`：允许访问的 AI API 域名，逗号分隔。
- `APP_PORT`：Docker 对外端口，默认 8080。
- `MAINTENANCE_ACCESS_TOKEN`：仅用于兼容性检查接口，可不配置。

数据库连接、会话密钥、加密主密钥和注册邀请码不适合写进代码：代码仓库及镜像通常会被复制、缓存或公开，写死后既容易泄露，也无法为不同服务器安全轮换。非敏感默认值已经内置。

## Render 兼容

现有 Render 环境无需立即修改。正式配置层会按以下顺序读取：

- `APP_DATABASE_URL` → `POC_WRITE_DATABASE_URL` → `DATABASE_URL`
- `SESSION_SECRET` → `POC_SESSION_SECRET`
- `ENCRYPTION_KEY` → `POC_ENCRYPTION_KEY`
- `AI_CALLS_ENABLED` → `POC_AI_CALLS_ENABLED`
- `ADMIN_ENABLED` → `POC_ADMIN_ENABLED`

迁移到自有服务器时只需使用左侧的新变量；旧的 `POC_WRITE_ENABLED` 与 `POC_SHARED_DATABASE_WRITE_ENABLED` 已不再需要。

### 免费实例健康检查

需要从外部机器每 10 分钟访问一次 Render 健康检查时，可在 Linux 上运行：

```sh
chmod +x scripts/keep-render-awake.sh
nohup ./scripts/keep-render-awake.sh > keep-render-awake.log 2>&1 &
```

停止脚本可执行 `pkill -f keep-render-awake.sh`。默认访问当前 Render 服务的 `/healthz`；迁移到其他地址后可通过 `HEALTH_URL=https://example.com/healthz` 覆盖。
## 本地构建

要求 Java 21+、Maven 3.9+、Node.js 22+：

```powershell
mvn -f backend\pom.xml clean package
java -jar backend\target\job-tracker.jar
```

也可以直接运行：

```sh
docker compose up -d --build
```