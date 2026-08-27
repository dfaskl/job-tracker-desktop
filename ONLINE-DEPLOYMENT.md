# 求职进度本：小规模多用户在线部署

线上版与原桌面版共存：

- 双击 `启动求职进度本.vbs` 仍运行原来的本地 JSON 版本。
- Render 免费 Web Service 执行 `npm start`，运行 `server-online.js`；持久数据保存在独立的 Neon 免费 PostgreSQL。
- GitHub 只保存代码；用户投递、日程、官网库、备份和 API 配置均不进入 Git。

## 已实现的安全边界

- 邮箱和密码登录；密码使用 Node.js `scrypt` 加随机盐哈希。
- 会话令牌只通过 `HttpOnly`、`SameSite=Lax` Cookie 保存，数据库只存令牌哈希。
- 业务数据、公司官网库、自动备份和 API 配置均通过 `user_id` 隔离。
- 管理员身份由服务端 `ADMIN_EMAIL` 提升并在每个管理接口重新校验；普通用户无法读取用户列表。
- 管理后台支持用户概览、停用、启用和永久删除，并记录最近管理操作。管理员不能停用或删除自己，也不能从后台删除其他管理员。
- 用户 API Key 使用 AES-256-GCM 加密；主密钥只存在 `ENCRYPTION_KEY` 环境变量。
- 设置接口只返回“已配置”和密钥末四位所需的掩码，不返回明文。
- AI 调用时仅在服务端内存中临时解密，前端请求中的 API 配置会被忽略。
- 自定义 AI 地址要求 HTTPS，并拒绝回环、局域网和本地域名，降低 SSRF 风险。
- 所有数据库表和索引在服务首次启动时自动创建。

注意：如果数据库和 `ENCRYPTION_KEY` 同时泄露，已加密的 API Key 仍可能被解密。生产环境必须限制平台账号权限、开启双重验证，并妥善备份主密钥。

## 免费部署：Render + Neon

当前 `render.yaml` 只创建一个免费的 Render Web Service，不会创建 Render 付费数据库。PostgreSQL 使用 Neon Free：截至 2026 年，免费额度包含每个项目每月 100 CU-hours、0.5 GB 存储和 5 GB 公网传输；数据库空闲时会缩容到零，没有 Render Free Postgres 的固定 30 天删除期限。实时限制以 <https://neon.com/pricing> 为准。

免费方案的限制：

- Render Web Service 连续 15 分钟没有请求会休眠，下次打开通常需要等待约一分钟。
- Neon 数据库空闲时也会休眠，首次查询可能稍慢。
- 免费服务没有生产可用性承诺；应用内自动备份和原数据位于同一个数据库，必须定期导出 JSON 做独立备份。
- Neon 免费额度适合当前小规模使用，达到额度后需要等待下个计费周期或升级。

### 1. 创建 Neon 免费数据库

1. 登录 <https://console.neon.tech/>，选择 **New Project**。
2. 项目名可填写 `job-tracker-db`，区域优先选择 AWS Singapore；PostgreSQL 版本保持默认。
3. 创建后在项目首页选择 **Connect**，优先复制带连接池的 **Pooled connection string**。
4. 连接串形如 `postgresql://...`，属于数据库密码，不得提交 GitHub、截图或分享。

### 2. 创建 Render Blueprint

1. 在 Render 控制台选择 **New → Blueprint**，连接 GitHub 仓库 `dfaskl/job-tracker-desktop`。
2. Blueprint Name 可填写 `job-tracker-production`，Branch 保持 `main`，Blueprint Path 填写 `render.yaml`。
3. Render 读取最新 `render.yaml` 后，配置预览中应只显示 **Create web service job-tracker-web (Free)**，不应再显示 `job-tracker-db`。
4. `DATABASE_URL` 填写刚从 Neon 复制的完整 Pooled connection string。
5. 在本机运行 `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`，将结果同时保存到密码管理器并填写为 `ENCRYPTION_KEY`。
6. 在本机运行 `node -e "console.log(require('crypto').randomBytes(18).toString('base64url'))"`，将结果保存并填写为 `REGISTRATION_CODE`。
7. 首次创建 Blueprint 时，`ADMIN_EMAIL` 可以暂时留空；等待健康检查通过后，先打开 Render 提供的 `onrender.com` 地址注册你的普通账号。
8. 确认账号注册成功后，在 Render 的 Environment 中把 `ADMIN_EMAIL` 设置为该账号的登录邮箱并保存。它不是密码，不要填写邀请码。
9. Render 重启服务时，已存在且与 `ADMIN_EMAIL` 匹配的账号会自动提升为管理员；重新登录后，侧栏会出现“管理后台”。
10. 所有目标用户注册完成后，在管理员后台概览中点击“关闭注册”，无需重启 Render。

此后推送到 GitHub 默认分支会先触发 GitHub Actions；语法检查和线上冒烟测试全部通过后，Render 才自动部署。Neon 数据库独立于代码部署，因此更新线上代码不会覆盖用户数据。

如果使用非 DeepSeek/OpenAI 的兼容 API，还需要把对应域名加入 Render 环境变量 `AI_ALLOWED_HOSTS`，多个域名用英文逗号分隔。

## 必需环境变量

| 变量 | 用途 |
|---|---|
| `DATABASE_URL` | PostgreSQL 连接地址 |
| `ENCRYPTION_KEY` | API Key 的服务端主加密密钥，至少 32 个随机字符 |
| `NODE_ENV=production` | 启用安全 Cookie 等生产配置 |
| `ADMIN_EMAIL` | 管理员登录邮箱；匹配的现有账号会在服务启动时被提升为管理员 |

建议同时配置：

- `REGISTRATION_CODE`：小规模用户的邀请码。
- `ALLOW_REGISTRATION`：尚未在管理员后台切换过注册状态时使用的初始默认值，默认为 `true`。
- `AI_ALLOWED_HOSTS`：允许使用的 AI API 域名白名单，例如 `api.deepseek.com,api.openai.com`。
- `SESSION_DAYS`：登录会话有效天数，默认 7 天。

## 管理员后台

管理员登录普通页面后，可通过侧栏底部的“管理后台”进入 `/admin.html`。后台只显示各账号的记录数量和 API Key 是否已配置，不读取投递内容或 API Key 明文。

- 概览中的“关闭注册 / 开启注册”会把状态保存到 PostgreSQL 并立即生效；首次切换后，数据库状态优先于 `ALLOW_REGISTRATION` 环境变量。
- 停用用户会立即删除该用户的现有会话，但不会删除数据；重新启用后可再次登录。
- 永久删除会级联删除该用户的投递、日程、公司官网库、API 配置、会话和数据库内备份，且无法撤销。
- 普通用户直接访问后台地址只能看到无权限提示，服务端管理接口返回 403。
- 修改 `ADMIN_EMAIL` 后需要保存 Render 环境变量并等待服务重启；已有账号随后重新登录即可看到管理员入口。

## 主密钥管理

`ENCRYPTION_KEY` 一旦丢失，数据库里的用户 API Key 无法恢复；一旦更换，旧密文也无法直接解密。因此：

1. 不要把真实值写入代码、GitHub、截图或日志。
2. 在密码管理器中保留一份安全备份。
3. 需要轮换时，应先实现逐条解密并重新加密的迁移流程，不能直接覆盖环境变量。

## 本地测试线上模式

需要本地 PostgreSQL。复制 `deployment.env.example` 中的变量到当前终端环境后执行：

```powershell
npm install
npm start
```

访问 `http://127.0.0.1:3000`。不要把真实环境变量文件提交到 Git。

## 发布前检查

```powershell
npm ci
npm run check
npm test
git diff --check
git status --short
```

首次真实部署还应测试：注册、登录、退出、跨账号隔离、投递保存、官网库、备份恢复、API Key 更新以及三项 AI 功能。
