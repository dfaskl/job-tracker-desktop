# 求职进度本：小规模多用户在线部署

线上版与原桌面版共存：

- 双击 `启动求职进度本.vbs` 仍运行原来的本地 JSON 版本。
- 云平台执行 `npm start`，运行 `server-online.js` 与 PostgreSQL 多用户版本。
- GitHub 只保存代码；用户投递、日程、官网库、备份和 API 配置均不进入 Git。

## 已实现的安全边界

- 邮箱和密码登录；密码使用 Node.js `scrypt` 加随机盐哈希。
- 会话令牌只通过 `HttpOnly`、`SameSite=Lax` Cookie 保存，数据库只存令牌哈希。
- 业务数据、公司官网库、自动备份和 API 配置均通过 `user_id` 隔离。
- 用户 API Key 使用 AES-256-GCM 加密；主密钥只存在 `ENCRYPTION_KEY` 环境变量。
- 设置接口只返回“已配置”和密钥末四位所需的掩码，不返回明文。
- AI 调用时仅在服务端内存中临时解密，前端请求中的 API 配置会被忽略。
- 自定义 AI 地址要求 HTTPS，并拒绝回环、局域网和本地域名，降低 SSRF 风险。
- 所有数据库表和索引在服务首次启动时自动创建。

注意：如果数据库和 `ENCRYPTION_KEY` 同时泄露，已加密的 API Key 仍可能被解密。生产环境必须限制平台账号权限、开启双重验证，并妥善备份主密钥。

## Render 部署

### 费用提醒

`render.yaml` 默认使用付费的 Starter Web Service 和 Basic-256MB PostgreSQL，目的是保持服务常驻，并避免免费 PostgreSQL 在 30 天后到期。Render 2026 年 7 月给出的参考组合约为每月 13 美元，数据库存储和超额流量另计；创建 Blueprint 时请以控制台显示的实时价格为准。当前价格说明：<https://render.com/pricing>。

1. 将本次代码推送到 GitHub。
2. 在 Render 控制台选择 **New → Blueprint**，连接当前仓库。
3. Render 会读取根目录 `render.yaml`，创建 Web Service 和 PostgreSQL。
4. 在本机运行 `node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"`，将结果同时保存到密码管理器并填写为 `ENCRYPTION_KEY`。
5. 为 `REGISTRATION_CODE` 填写一个只分享给目标用户的强邀请码。
6. 完成首次部署后打开站点，注册管理员账号。
7. 用户注册完成后，可将 `ALLOW_REGISTRATION` 改为 `false`，阻止继续注册。

此后推送到 GitHub 默认分支会先触发 GitHub Actions；语法检查和线上冒烟测试全部通过后，Render 才自动部署。数据库独立于代码部署，因此更新线上代码不会覆盖用户数据。

如果使用 Railway，可创建一个 Node 服务和一个 PostgreSQL 服务，然后按 `deployment.env.example` 配置同名环境变量；启动命令为 `npm start`。

## 必需环境变量

| 变量 | 用途 |
|---|---|
| `DATABASE_URL` | PostgreSQL 连接地址 |
| `ENCRYPTION_KEY` | API Key 的服务端主加密密钥，至少 32 个随机字符 |
| `NODE_ENV=production` | 启用安全 Cookie 等生产配置 |

建议同时配置：

- `REGISTRATION_CODE`：小规模用户的邀请码。
- `ALLOW_REGISTRATION`：注册结束后设为 `false`。
- `AI_ALLOWED_HOSTS`：允许使用的 AI API 域名白名单，例如 `api.deepseek.com,api.openai.com`。
- `SESSION_DAYS`：登录会话有效天数，默认 7 天。

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
