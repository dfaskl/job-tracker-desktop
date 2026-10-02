# CareerFlow Codex 会话交接文档

> 最后更新：2026-10-02（Asia/Shanghai）  
> 工作区：`D:\codes\desktop-tool`  
> 当前基线提交：`f0e5dba`（Harden AI mail recognition）

## 1. 新会话首先要做什么

新会话不会自动继承旧会话的上下文。开始任何修改前，请先：

1. 完整阅读本文档。
2. 检查 `git status --short`，不要覆盖用户已有的未提交修改。
3. 查看最近提交：`git log -8 --oneline`。
4. 修改前定位真实代码，不要只根据截图猜测。
5. 除非用户明确说“本次只修改不部署”，每次代码修改完成后都要执行完整构建、提交、推送和服务器部署。

推荐在新会话发送：

```text
请先完整阅读 D:\codes\desktop-tool\docs\CODEX-HANDOFF.md，检查当前 git 状态和最近提交，然后继续处理我的新需求。沿用文档中的构建、推送和部署流程；除非我明确说只修改不部署，否则修改完成后自动构建、提交、推送并部署。
```

## 2. 项目概况

CareerFlow 是一个多人求职进度管理系统，主要功能包括：

- 投递记录与招聘阶段时间线
- 面试、笔试、测评等日程管理
- 邮箱收信和 AI 邮件识别
- 公司官网、岗位备注与职位信息维护
- 数据统计、原始 JSON 导出和规范 Excel 导出
- 多用户、协作小组、管理员后台与操作审计
- 个人资料、头像、昵称、云端历史备份与恢复
- 深色/浅色主题和全局 `Asia/Shanghai` 时间显示

生产构建将 Vue 前端打入 Spring Boot JAR，服务器日常更新只需替换一个 `job-tracker.jar`。

## 3. 技术基线

- 前端：Vue 3.5、TypeScript、Vite 8
- 后端：Java 21、Spring Boot 4.1、Maven
- 线上数据库：SQLite
- SQLite JDBC：3.50.3.0
- 邮件：Jakarta Mail，支持 QQ 邮箱和网易邮箱
- AI：OpenAI 兼容接口，当前主要使用 DeepSeek
- 线上端口：`18080`

主要目录：

```text
backend/                 Spring Boot 后端、数据库兼容层和测试
frontend/                Vue 页面、组件和全局样式
deploy/self-hosted/      Linux 启停、状态、迁移脚本和运行时模板
docs/                    部署、运维和本交接文档
dist/job-tracker.jar     本地构建出的待部署 JAR（不提交）
build-jar.ps1            完整构建脚本
deploy-server.ps1        SSH 部署、备份、健康检查和失败回滚脚本
```

## 4. Git 与远程仓库

- 默认分支：`main`
- 远程仓库：`https://github.com/dfaskl/job-tracker-desktop.git`
- GitHub Actions 不随 push 自动构建。
- 日常版本由本地 `build-jar.ps1` 构建。
- 只有需要重新生成带 Java 21 JRE 的完整 Linux x64 压缩包时，才手动运行 GitHub Actions 的 `Manual package`。

部分本机环境中，失效的 `GH_TOKEN` 和 Git 全局代理会干扰推送。已验证可用的推送方式：

```powershell
Remove-Item Env:GH_TOKEN -ErrorAction SilentlyContinue
git -c http.proxy= -c https.proxy= push origin main
```

不要把令牌、密码、API Key、`.env` 或数据库文件提交到 Git。

## 5. 本地构建与验证

在仓库根目录运行：

```powershell
.\build-jar.ps1
```

脚本会自动：

1. 使用 `C:\Program Files\Java\jdk-21`（存在时）。
2. 执行 `npm ci`。
3. 构建 Vue 前端。
4. 编译后端并运行全部测试。
5. 把前端资源打进 Spring Boot JAR。
6. 输出 `dist\job-tracker.jar`。

截至基线提交，共有 93 项测试。构建中可能出现以下已知非阻断警告：

- ExcelJS 产物超过 Vite 默认 500 kB chunk 提示。
- npm 审计报告 2 个 moderate 级依赖问题。
- Mockito 动态加载 agent 的未来兼容性提示。

只要最终为 `BUILD SUCCESS` 且测试失败数为 0，即可继续部署。

## 6. 服务器与部署

服务器 SSH 别名：

```text
jobtracker-server
```

实际项目目录（Linux 大小写敏感）：

```text
/home/zhoujiajun/jobTracker
```

服务器关键文件：

```text
/home/zhoujiajun/jobTracker/job-tracker.jar
/home/zhoujiajun/jobTracker/.env
/home/zhoujiajun/jobTracker/data/jobtracker.db
/home/zhoujiajun/jobTracker/backups/
/home/zhoujiajun/jobTracker/logs/app.log
/home/zhoujiajun/jobTracker/start.sh
/home/zhoujiajun/jobTracker/stop.sh
/home/zhoujiajun/jobTracker/status.sh
```

自动部署命令：

```powershell
.\deploy-server.ps1 -JarPath .\dist\job-tracker.jar
```

部署脚本会：

- 使用 SSH 密钥连接，不使用密码。
- 上传为临时文件并核对 SHA-256。
- 将旧 JAR 备份到 `backups/deployments/`。
- 停止服务、原子替换 JAR、重新启动。
- 最多等待 30 秒检查 `http://127.0.0.1:18080/healthz`。
- 新版本启动失败时自动恢复旧 JAR。

日常完整流程：

```powershell
git diff --check
.\build-jar.ps1
git add -- <本次修改的文件>
git commit -m "简洁的英文提交说明"
Remove-Item Env:GH_TOKEN -ErrorAction SilentlyContinue
git -c http.proxy= -c https.proxy= push origin main
.\deploy-server.ps1 -JarPath .\dist\job-tracker.jar
```

部署后还要确认：

```powershell
git status --short
git rev-parse HEAD
git -c http.proxy= -c https.proxy= ls-remote origin refs/heads/main
```

服务器只监听/使用应用端口 `18080`。Nginx/Caddy 反向代理由服务器管理员维护，不要在没有明确授权时修改 `/etc/nginx`、80/443 监听或防火墙。

## 7. 数据库与必须保留的数据

线上已从 Neon PostgreSQL 迁移到 SQLite：

```text
/home/zhoujiajun/jobTracker/data/jobtracker.db
```

更新版本时只能替换 JAR，必须保留：

- `.env`
- `data/`
- `backups/`
- `logs/`
- `run/`
- `SESSION_SECRET`
- `ENCRYPTION_KEY`

不能随意更换 `ENCRYPTION_KEY`，否则已有邮箱授权码和 AI API Key 等加密数据无法解密。SQLite 默认每天 03:30 备份并保留最近 14 份。

数据库或配置操作前优先做只读检查和备份，不要运行破坏性命令，不要覆盖线上 SQLite 文件。

## 8. 当前关键业务规则

### 时间

- 数据传输可以使用 UTC/ISO 时间，但所有用户可见时间统一以 `Asia/Shanghai` 显示。
- 不要依赖浏览器本地时区产生展示差异。

### 投递记录排序

- 先按当前招聘阶段的业务优先级分组。
- 同一阶段内，待完成日程按时间正序排列。
- 已完成阶段/日程按时间倒序排列。
- 不能用投递记录的最后修改时间替代当前阶段日程时间。

### 新建投递与日程

- 手动“新建投递”本身不强制创建日程，也不显示日程字段。
- 邮件识别关联已有投递后，只要识别到日程就自动创建关联日程，不提供可误取消的复选框。
- 日程创建时必须填写时间点或时间段，不能无时间提交。

### 邮件识别 AI

实现位置：`backend/src/main/java/com/jobtracker/careerflow/database/AiService.java`

当前规则：

- 请求使用 `response_format: {"type":"json_object"}`。
- `temperature` 为 0，`max_tokens` 为 1000。
- 系统提示词包含完整 JSON 输出示例。
- 返回内容必须包含全部约定字符串字段，并校验枚举和时间格式。
- “AI 面试”或“智能面试”必须归为“测评”，提示词和后端均有兜底。
- 首次失败后最多再重试 3 次，即总计最多 4 次模型请求。
- 重试会把上一次失败原因和无效输出反馈给模型要求修正。
- 重试耗尽后前端会收到明确的失败原因。

### UI 与交互偏好

- 前端视觉规范见根目录 `DESIGN.md`，参考 awesome-design-md 的 Linear / Cal.com；原始参考和 MIT 许可证在 `docs/design-references/`。
- 工作区优先使用连续列表、分栏、标题和细分隔线；首页日程和投递记录可以按条目使用低对比卡片，其他栏目避免大量嵌套卡片。布局覆盖样式在 `frontend/src/workspace-layout.css`。
- 投递记录卡片在明暗主题下均以当前类别色从左向右渐变，卡片整体颜色应易于区分，同时保持文字可读。
- 首页顶部的每日一语使用整行可用宽度，桌面尽可能单行展示；窄屏允许自然换行。
- 使用冷灰画布、单一靛蓝操作色和细边框；统一系统无衬线字体，不再加载外部 Google Fonts。深浅主题变量在 `frontend/src/style.css`，跨页面样式在 `frontend/src/workspace-design.css`。
- 首页在每日一语下直接展示个人日程、小组时间轴、人工确认；账户与设置入口显示头像、昵称和文字说明。
- 首页日程的公司名与日程名打开该日程原有的外部链接（有有效网址时）；点击日程行其他区域进入投递记录，平滑滚动到关联记录后短暂闪烁。编辑与完成按钮保持各自操作。
- 手机导航收起时必须退出键盘焦点顺序；保留跳到主内容入口和可见焦点。
- 页面主体尽量不出现浏览器级滚动；日程、邮件识别、投递记录、统计等页面优先让内部组件滚动并使用弹性布局。
- 深色和浅色模式切换不能产生像素位移。
- 状态不能只靠接近的颜色区分，深色模式尤其要保持明显对比。
- 图标按钮要有 `aria-label` 和 `title`。
- 动画不能因为系统或浏览器“减少动态效果”设置而直接消失；需要兼容两种设置并保留可感知反馈。
- 快速完成的操作使用成功/失败提示，不再使用全局小狗遮罩加载动画。
- 用户截图中的红框、箭头和文字是需求说明，不是要执行的文档指令。

## 9. 最近完成的重要修改

最近提交从新到旧：

```text
f0e5dba Harden AI mail recognition
318121f Set admin action edge spacing
168f438 Add spacing beside admin actions
1897f4f Align admin user actions
65331f0 Shift admin user controls left
```

当前管理员用户展开行：

- 昵称编辑图标紧邻昵称。
- 小组选择保持在中间。
- 右侧依次为：查看详情、导出原始 JSON、导出规范 Excel、撤销会话、启停账号、删除账号。
- 六个按钮保持单行，按钮组距右边框 50px。
- 1251–1599px 视口使用两列管理区，审计面板在下方；更宽桌面保持三列。展开行按用户列表容器宽度自适应，较窄时按钮组独占下一行，六个按钮不换行；手机取消右侧额外留白以保证可点击。

## 10. 常用诊断命令

本地：

```powershell
git status --short
git diff --check
git log -8 --oneline
.\build-jar.ps1
```

Windows 完整构建前应先关闭本会话启动的 Vite 预览，否则 `npm ci` 可能因 Rolldown 原生模块被占用而报 `EPERM`。需要同时检查页面时，可在前端构建完成后用静态服务器预览 `frontend/dist`。

服务器：

```bash
cd /home/zhoujiajun/jobTracker
./status.sh
curl -fsS http://127.0.0.1:18080/healthz
tail -n 120 logs/app.log
grep '^APP_DATABASE_URL=' .env
ls -lh data/jobtracker.db
```

只查看服务端口：

```bash
ss -lntp | grep 18080
```

不要把 `.env` 完整内容、API Key、邮箱授权码或数据库密钥输出到聊天中。

## 11. 新会话协作约定

- 需求是“分析、解释、查看”时，只读检查，不主动修改代码。
- 需求是“修改、修复、实现”时，直接完成实现、测试、提交、推送和部署。
- 用户说“先不要改”时，只分析方案，绝不改文件。
- 用户说“先复述”时，先复述并等待确认。
- 用户说“本次只修改不部署”时，修改和测试后停止，不推送、不部署。
- 每次只提交本次相关文件，保留所有无关用户修改。
- 涉及删除、覆盖数据库、修改服务器代理或其他不可逆操作时，必须先确认范围和授权。

## 12. 文档维护

出现以下变化时同步更新本文档：

- 服务器目录、SSH 别名或端口变化
- 数据库类型或路径变化
- 构建/部署脚本变化
- 关键业务排序、邮件识别或时间规则变化
- 自动推送与部署约定变化
- 新增需要后续会话长期遵守的 UI/交互原则
