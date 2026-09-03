# Java 持久化会话迁移

第 3 阶段为现有 `/api/poc/auth/*` 登录流程增加 PostgreSQL 持久化模式。该模式默认关闭；未启用时继续使用原型已有的四小时 HMAC 签名 Cookie。

## 安全与兼容规则

- 持久化会话只使用 `POC_WRITE_DATABASE_URL` 指向的独立测试数据库，不写入生产 `DATABASE_URL`。
- 必须同时满足职位 CRUD 沙箱隔离条件，并设置 `POC_PERSISTENT_SESSION_ENABLED=true`。
- 登录成功后生成 32 字节安全随机令牌；浏览器保存原始令牌，数据库只保存 SHA-256 哈希。
- SHA-256 十六进制格式与旧 Node.js `tokenHash()` 完全相同。
- 会话写入旧结构的 `sessions(token_hash,user_id,expires_at)` 表，并关联测试库中的同邮箱用户。
- 会话校验同时检查未过期和用户未停用；注销立即删除令牌哈希。
- Cookie 保持 `HttpOnly`、`Secure`（HTTPS）、`SameSite=Strict` 和根路径限制。

## 环境变量

- `POC_PERSISTENT_SESSION_ENABLED=true`：启用数据库会话。
- `POC_SESSION_DAYS`：可选，范围 1–30，默认 7 天。
- `POC_WRITE_DATABASE_URL` 与 `POC_WRITE_ENABLED=true`：沿用独立测试数据库配置。

状态接口：`GET /api/poc/session-mode`。它只返回模式、隔离状态和有效期，不返回令牌、连接串或用户信息。
