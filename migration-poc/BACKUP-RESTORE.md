# 备份列表与恢复迁移

第 4 阶段只在独立测试数据库中开放备份列表和恢复验证，复用 `POC_WRITE_DATABASE_URL` 与 `POC_WRITE_ENABLED=true` 的安全边界。

## 恢复事务

恢复操作在一个数据库事务中依次执行：

1. 按当前登录邮箱锁定测试库的 `user_data`。
2. 比较页面提交的 `expectedCurrentUpdatedAt`，阻止覆盖列表加载后发生的新变更。
3. 校验目标备份是 JSON 对象，且包含 `applications` 和 `events` 数组。
4. 将恢复前的当前数据写入 `data_backups`，原因为 `before-restore`。
5. 用目标备份替换 `user_data.data` 并更新时间。
6. 只保留该测试用户最近 30 份备份，然后提交事务。

任一步失败都会回滚，不留下半恢复状态。

## 接口

- `GET /api/poc/backup-sandbox/status`
- `GET /api/poc/backup-sandbox/backups`
- `POST /api/poc/backup-sandbox/backups/{id}/restore`

恢复请求必须同时携带最新的 `expectedCurrentUpdatedAt`，并将 `confirmation` 精确填写为 `恢复`。备份数据和数据库地址都不会通过状态接口暴露。
