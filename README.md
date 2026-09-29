# CareerFlow 求职进度本

CareerFlow 是一个面向个人与小组协作的求职投递管理系统，用于集中管理职位投递、招聘进度、面试日程、邮件通知和数据统计。

当前仓库只包含正式版系统：Vue 前端在构建时打入 Spring Boot JAR，可使用单个 Java 进程部署；自托管环境默认使用 SQLite，数据与程序文件分离保存。

## 主要功能

- 投递记录与多阶段招聘进度时间轴
- 面试、笔试、测评等日程管理
- 招聘邮件收集与智能识别
- 公司官网库、备注和职位信息维护
- 投递趋势、阶段分布与渠道统计
- 多用户、协作小组与管理员后台
- 深色模式、数据导入导出和历史备份

## 技术架构

- 前端：Vue 3、TypeScript、Vite
- 后端：Java 21、Spring Boot、Maven
- 数据库：SQLite（自托管默认），同时保留 PostgreSQL 兼容能力
- 发布：GitHub Actions 自动测试并生成 Linux x64 自带 JRE 部署包

## 目录结构

```text
.
├── migration-poc/
│   ├── frontend/              Vue 前端
│   ├── backend/               Spring Boot 后端
│   ├── deploy/self-hosted/    Linux 自托管脚本与环境变量示例
│   ├── scripts/               构建和部署辅助脚本
│   └── SELF-HOSTED-JRE.md     无 Docker 部署说明
├── .github/workflows/ci.yml   自动测试与打包流程
└── render.yaml                Render 兼容部署配置
```

## 本地构建

需要 Java 21、Maven 3.9 和 Node.js 22：

```powershell
mvn -f migration-poc/backend/pom.xml clean package
java -jar migration-poc/backend/target/job-tracker.jar
```

Maven 会自动安装前端依赖、构建 Vue、运行后端测试，并将前端静态资源打入最终 JAR。

## Linux 自托管

推荐从 GitHub Actions 的 `CI` 工作流下载 `job-tracker-linux-x64`。部署包自带 Java 21 JRE，服务器不需要安装 Docker 或升级系统 Java。

完整步骤见 [Linux 自托管部署说明](migration-poc/SELF-HOSTED-JRE.md)。当前服务器部署通常包含：

```text
jobTracker/
├── job-tracker.jar
├── runtime/
├── data/jobtracker.db
├── backups/
├── logs/
├── .env
├── start.sh
├── stop.sh
└── status.sh
```

SQLite 数据库和备份不会包含在发布包中。更新版本时应保留服务器上的 `.env`、`data/`、`backups/` 和 `logs/`。

## 数据安全

- SQLite 数据库默认位于 `data/jobtracker.db`。
- 应用可每天自动生成 SQLite 一致性备份并按保留数量轮换。
- `SESSION_SECRET` 与 `ENCRYPTION_KEY` 必须保存在服务器环境文件中，不应提交到 Git。
- 升级程序前建议额外复制数据库文件或确认最近一次自动备份可用。

## 健康检查

应用启动后可访问：

```text
GET /healthz
```

正常响应：

```json
{"ok":true}
```
