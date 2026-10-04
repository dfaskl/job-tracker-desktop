# CareerFlow 求职进度本

CareerFlow 是一个面向个人与小组协作的求职管理系统，用于统一维护职位投递、招聘进度、面试日程、招聘邮件和统计数据。

前端使用 Vue 3，后端使用 Spring Boot。生产构建会把前端静态资源打入 Spring Boot JAR，因此部署时只需要运行一个 Java 进程。自托管环境推荐使用 SQLite，也支持 PostgreSQL。

## 主要功能

- 投递记录和多阶段招聘进度时间轴
- 面试、笔试、测评等日程管理
- 招聘邮件收集与智能识别
- 公司官网、岗位备注和职位信息维护
- 面试回顾归档、AI 考点整理与复习讲解
- 投递趋势、阶段分布与渠道统计
- 多用户、协作小组和管理员后台
- 深色模式、数据导入导出和历史备份
- 图文并茂、支持键盘翻页的新手使用指引

## 面试复习工作台

在日程编辑中记录面试回顾后，可以在「面试总结」页面把多场面试的内容整理成个人复习材料。页面分为面试回顾列表、考点分类和考点详情三部分；点击回顾卡片可查看原始记录，选择分类后查看归纳问题和详细学习讲解。

汇总分为分类和回答两个阶段：

1. 系统先按批次分析面试回顾，将问题分为项目考点、通用知识点和其他问题。项目考点按简历中的每段实习或项目分别归类；通用知识点按较宽泛的技术主题整理；自我介绍、沟通和背景确认等问题归为其他。
2. 分类时会合并考察目标相同的不同问法，同时保留原始问法和出现频次，方便回看来源。
3. 分类结果保存后，系统再分批生成每个归纳问题的学习讲解。项目类讲解会附带对应的简历经历作为上下文；其他问题不会额外携带简历经历。
4. 页面通过 SSE 显示处理进度，并在回答生成时逐步展示内容。每个阶段和批次都会保存状态，遇到中断后可以继续未完成的工作。
5. 更新面试回顾时，系统会根据记录指纹只重新分类发生变化的记录，并合并回已有结果；完整且未变化的问题回答会保留。点击「重新汇总全部考点」会先要求确认，再执行全量重建。

个人主页的「我的简历」支持维护教育经历（学校、专业、时间）、实习经历和项目经历。简历内容保存后以只读方式展示，点击「编辑简历」可再次修改；面试总结页面的简历入口会跳转并定位到该区域。

## 新手使用指引

登录后从左侧导航栏点击「使用指引」，可以打开分步介绍页面，了解首页、投递记录、日程、邮件识别、统计、面试复习和小组协作。每页配有界面示意图和操作提示，可使用上一页/下一页按钮、页码圆点或键盘左右方向键浏览。

## 技术栈

- 前端：Vue 3、TypeScript、Vite
- 后端：Java 21、Spring Boot、Maven
- 数据库：SQLite 或 PostgreSQL
- 发布：本地脚本生成可直接上传服务器的 JAR；GitHub Actions 仅保留手动生成完整 Linux x64 部署包的入口

## 仓库结构

```text
.
├── backend/                 Spring Boot 后端、数据库结构与后端测试
├── frontend/                Vue 3 前端、样式与前端测试
├── deploy/self-hosted/      Linux 自托管环境模板及启停脚本
├── docs/                    部署和运维文档
├── scripts/                 Docker 自托管初始化脚本
├── data/                    本地 SQLite 数据目录（数据库文件不提交）
├── .github/workflows/       持续集成和部署包构建
├── Dockerfile               应用镜像构建
├── docker-compose.yml       PostgreSQL + 应用的容器部署
└── render.yaml              Render 部署配置
```

## 本地构建

需要 Java 21、Maven 3.9 和 Node.js 22。在仓库根目录运行：

```powershell
.\build-jar.ps1
```

脚本会调用 Maven 自动执行 `npm ci`、构建 Vue、运行后端测试，并将前端资源复制到最终 JAR。构建成功后的服务器更新文件位于 `dist/job-tracker.jar`。

如需在本地直接启动，也可以运行：

```powershell
java -jar .\dist\job-tracker.jar
```

应用默认读取环境变量或根目录 `.env` 中的配置。可参考 [.env.example](.env.example) 和 [deploy/self-hosted/env.example](deploy/self-hosted/env.example)。生产环境至少需要配置：

- `APP_DATABASE_URL`
- `SESSION_SECRET`
- `ENCRYPTION_KEY`
- `ADMIN_EMAIL`

## 部署

### Linux 单机部署

日常更新推荐运行 `.\build-jar.ps1`，然后把 `dist/job-tracker.jar` 上传到服务器替换旧 JAR。GitHub Actions 不再由 push 自动运行；只有需要重新生成自带 Java 21 JRE 的完整部署包时，才在仓库的 **Actions → Manual package → Run workflow** 中手动执行。

完整的安装、更新、回滚和 SQLite 备份方法见 [自托管部署文档](docs/SELF-HOSTED-DEPLOYMENT.md)。更新版本时必须保留服务器上的 `.env`、`data/`、`backups/` 和 `logs/`。

### Docker Compose

复制 `.env.example` 为 `.env`，填写密钥后运行：

```sh
docker compose up -d --build
```

此模式会同时运行 PostgreSQL 和 CareerFlow，数据库保存在 `jobtracker_data` 数据卷中。

## 数据安全

- SQLite 数据库默认位于 `data/jobtracker.db`。
- SQLite 可每天生成一致性备份，并按配置自动轮换。
- `.env`、数据库、日志、备份和密钥文件均被 Git 忽略。
- `SESSION_SECRET` 与 `ENCRYPTION_KEY` 不应提交到仓库；升级时也不要随意更换。

## 健康检查

应用启动后访问：

```text
GET /healthz
```

正常响应：

```json
{"ok":true}
```
