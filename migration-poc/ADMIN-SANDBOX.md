# 管理员后台迁移沙箱

第 6 阶段把旧 Node.js 管理后台的概览、注册开关、用户启停、用户数据流程、删除确认和审计日志迁移到 Vue + Java Demo。

## 安全边界

- 默认关闭；必须同时设置 `POC_WRITE_ENABLED=true` 与 `POC_ADMIN_ENABLED=true`。
- 只连接 `POC_WRITE_DATABASE_URL`，并复用写入沙箱对 `DATABASE_URL` 的不同库校验。
- 当前登录邮箱还必须在独立测试库中拥有 `is_admin=true`，否则返回 403。
- 不提供密码、密码散列、API Key 密文或明文的查看接口；概览只返回是否配置 API Key。
- 不能停用或删除自己，也不能通过迁移后台停用或删除任何管理员。
- 停用测试账号时撤销该账号在测试库中的全部持久会话。
- 删除测试账号必须输入与目标账号完全匹配的邮箱，删除由数据库事务保护。
- 注册开关首先读取测试库 `system_settings.registration_open`，数据库设置优先于环境变量。

## 接口

- `GET /api/poc/admin-sandbox/status`
- `GET /api/poc/admin-sandbox/overview`
- `GET /api/poc/admin-sandbox/users/{id}/details`
- `PATCH /api/poc/admin-sandbox/settings/registration`
- `PATCH /api/poc/admin-sandbox/users/{id}`
- `DELETE /api/poc/admin-sandbox/users/{id}`

除状态接口外均要求 `poc_session` 登录 Cookie；修改接口还执行同源检查。

## Render 默认行为

`migration-poc/render.yaml` 不声明 `POC_WRITE_DATABASE_URL`、`POC_WRITE_ENABLED` 或 `POC_ADMIN_ENABLED`，所以公开 Demo 只显示迁移成果和关闭原因，不会访问生产库执行管理员操作。
