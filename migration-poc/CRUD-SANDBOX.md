# 职位申请 CRUD 迁移沙箱

第 1 阶段在 Vue + Java 原型中加入了职位申请的新增、修改和删除能力。该能力默认关闭，现有 `DATABASE_URL` 只用于登录验证和数据读取。

## 安全边界

- 只有 `POC_WRITE_ENABLED=true` 时才考虑开放写入。
- 必须另外配置 `POC_WRITE_DATABASE_URL`，并且其标准化数据库地址不能与 `DATABASE_URL` 相同。
- 任一条件不满足时，后端拒绝所有沙箱数据操作，页面显示“安全关闭”。
- 不要把数据库连接串、会话密钥或访问令牌提交到仓库。

## 测试库准备条件

独立测试 PostgreSQL 数据库需要保留旧系统的兼容表结构，并至少包含用于验证的账号数据：

- `users`：包含与生产登录账号相同邮箱的启用用户。
- `user_data`：包含该测试用户的数据副本，且 `data` 中的 `applications` 和 `events` 必须为数组。
- `data_backups`：结构与旧系统一致，用于每次写入前保存整份业务 JSON。

建议只复制专门用于测试的账号，不要把不必要的真实用户数据复制到沙箱。

## Render 环境变量

在 Demo Web Service 的 Environment 页面中增加：

- `POC_WRITE_DATABASE_URL`：独立测试数据库的 Internal Database URL。
- `POC_WRITE_ENABLED`：确认连接的是测试库后设为 `true`。

保存后等待 Render 重新部署。打开 Demo 页面，“职位申请 CRUD 迁移沙箱”应从“安全关闭”变为“独立测试库”。

## 接口

- `GET /api/poc/application-sandbox/status`：返回写入开关、配置和隔离状态，不返回连接串。
- `GET /api/poc/application-sandbox/applications`：读取当前登录邮箱在测试库中的职位申请。
- `POST /api/poc/application-sandbox/applications`：新增职位申请。
- `PUT /api/poc/application-sandbox/applications/{id}`：修改职位申请，使用 `expectedUpdatedAt` 检测并发覆盖。
- `DELETE /api/poc/application-sandbox/applications/{id}`：删除职位申请及其关联日程。

所有数据接口都要求先通过旧账号登录；所有变更接口还执行同源请求检查。每次变更会在同一事务内先写入 `data_backups`，再更新 `user_data`，最后清理到最近 30 份备份。
