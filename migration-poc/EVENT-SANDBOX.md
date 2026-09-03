# 日程与月历迁移沙箱

第 2 阶段复用职位申请 CRUD 的独立测试数据库和安全开关。未同时配置 `POC_WRITE_DATABASE_URL` 与 `POC_WRITE_ENABLED=true` 时，日程接口全部拒绝读写，现有 `DATABASE_URL` 保持只读。

## 已覆盖行为

- 新增、编辑和删除日程。
- 日程类型兼容：`测评`、`笔试`、`面试`、`Offer`、`其他`。
- 时间点只保存 `startsAt`；时间段保存 `startsAt` 和 `endsAt`，且结束时间必须晚于开始时间。
- 完成或错过时间段时记录实际 `completedAt`；恢复时删除 `completedAt`。
- 时间点完成后仍使用原 `startsAt` 作为进展时间。
- 创建测评、笔试、面试或 Offer 日程时，同步岗位阶段、状态和时间线。
- 删除日程时清理对应岗位时间线；非终态岗位按剩余最新日程回退阶段。
- 月历对未完成时间段分别显示开始、进行中和截止；完成后只显示实际完成时间点。
- 所有变更在测试库事务中执行，写入前保存整份业务 JSON，并保留最近 30 份备份。

## 接口

- `GET /api/poc/event-sandbox/status`
- `GET /api/poc/event-sandbox/events`
- `POST /api/poc/event-sandbox/events`
- `PUT /api/poc/event-sandbox/events/{id}`
- `POST /api/poc/event-sandbox/events/{id}/resolution`
- `DELETE /api/poc/event-sandbox/events/{id}`

`resolution` 请求的 `action` 支持 `complete`、`miss` 和 `restore`。修改、状态变更和删除均要求携带服务端最近返回的 `expectedUpdatedAt`，用于阻止旧页面覆盖更新后的记录。
