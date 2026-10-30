# Docker 打包与服务器部署

本方案在 Windows 的 Docker Desktop 构建 Linux amd64 镜像，前端与 Java 构建均在容器内完成；导出镜像后通过 SSH 上传服务器，由 Compose 运行。服务器不需要连接 Docker Hub，也不需要安装 Node.js、Maven 或 Java。

## 当前迁移状态

2026-10-30 检查：本机 Docker Desktop 已启动，已有测试镜像可运行。Docker Hub 的 HTTPS 连接超时，基础镜像暂时无法拉取；服务器 `zhoujiajun` 无权访问 `/var/run/docker.sock`。因此线上仍运行 JAR，尚未迁移到容器。需要先解决这两项，才能完成项目镜像的实际构建和线上切换。

## 一次性准备

1. 本机启动 Docker Desktop，执行 `docker version`，确认同时显示 Client 和 Server。
2. 执行 `docker pull node:22.22.0-alpine`、`docker pull maven:3.9.9-eclipse-temurin-21`、`docker pull eclipse-temurin:21-jre-jammy`。若超时，检查 Docker Desktop 自己的代理设置；只配置 Git 或终端代理不等于给 Docker 引擎配置代理。
3. 请服务器管理员授予账户使用 Docker 的权限，或提供管理员维护的 rootless Docker。Docker 组权限等同于很高的主机权限，应由管理员决定；项目脚本不会更改系统权限。
4. 重新连接 SSH，确认 `docker info` 与 `docker compose version` 成功。Compose 至少 2.30，因配置使用 `env_file.format: raw`，避免密钥中的 `$` 被插值。
5. 现有服务器应保留 `.env`、`data/jobtracker.db`、`backups/`、`start.sh`、`stop.sh` 和 `job-tracker.jar`。脚本会检查实际数据库路径，拒绝自动迁移其他位置或 PostgreSQL。

## 打包和部署

```powershell
# 镜像中执行前端与后端测试，打包并导出镜像
.\build-docker.ps1

# 打包完成后单独部署
.\deploy-docker-server.ps1

# 或一条命令完成
.\build-docker.ps1 -Deploy
```

产物为 `dist/careerflow-image.tar` 与 `dist/careerflow-image.json`，不提交 Git。元数据记录镜像标签、镜像 ID 和导出包 SHA256。部署脚本先检查服务器 Docker 权限，上传后核对文件和镜像身份，随后才停止旧进程。首次切换存在短暂不可访问时间。

服务监听地址仍为原服务器的 `18080` 端口。不要在容器启动后再次运行旧 `start.sh`；它保留用于首次迁移回滚。

## 持久化与密钥

- `data/` 挂载到 `/app/data`，继续使用同一个 SQLite 数据库，包括账户、会话、投递、日程、MCP 令牌和站内配置。
- `backups/` 挂载到 `/app/backups`，自动 SQLite 备份写入其 `sqlite/` 子目录。
- 旧 `.env` 留在服务器；脚本在服务器本地转换成权限为 600 的 `.env.docker`，不上传或输出密钥。沿用 `SESSION_SECRET`、`ENCRYPTION_KEY` 等配置。
- 容器使用服务器账户 UID/GID 运行，不需要递归更改原数据目录的归属。
- Java 堆限制沿用 `JAVA_XMS`、`JAVA_XMX`，容器内存上限默认 1536MB；调高 Java 堆时也要相应修改 Compose 上限。
- 日志写入 Docker，按单文件 10MB、最多 3 个文件轮换；旧 `logs/` 保留。
- 镜像不包含服务器 `.env`、数据库、备份、日志或 Git 元数据。

## 运维与回滚

在服务器项目目录运行：

```bash
docker compose -p careerflow --env-file .docker-release.env -f docker-compose.server.yml ps
docker compose -p careerflow --env-file .docker-release.env -f docker-compose.server.yml logs --tail=100 app
docker compose -p careerflow --env-file .docker-release.env -f docker-compose.server.yml restart app
```

容器启动健康检查失败时，脚本自动尝试恢复原运行方式：首次部署恢复旧 JAR，后续部署恢复前一份 Compose 配置和镜像。停止旧服务后，脚本会先创建并检查 `backups/docker-migration/` 中的 SQLite 一致性快照。失败时保留快照，自动回滚不会覆盖数据库。

首次部署成功后若要人工恢复 JAR，先执行上面的 Compose 命令并把最后的操作改成 `down`，确认容器停止后执行 `./start.sh`。不要使用 `down -v`、不要删除 `data/`，也不要在应用运行中直接覆盖数据库。

旧镜像暂不自动删除，以保留回滚能力。确认稳定后可按明确的旧镜像标签删除；不要运行无差别的带卷清理命令。

## 旧部署方式

`build-jar.ps1`、`deploy-server.ps1` 保留用于兼容和回滚。原 `docker-compose.yml` 仍是 PostgreSQL 新部署示例；当前生产 SQLite 部署使用的是 `docker-compose.server.yml`，两者不要混用。原先的 `scripts/setup-self-host.*` 也只针对 PostgreSQL 示例。

参考：[Docker Compose 服务配置](https://docs.docker.com/reference/compose-file/services/)。
