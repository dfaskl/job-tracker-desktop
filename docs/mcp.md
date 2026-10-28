# 通过 Codex 操作自己的账户

CareerFlow 提供账户隔离的 Streamable HTTP MCP 接口，复用网站的投递、日程校验、记录版本检查和历史备份。无需给普通用户配置 SSH 隧道；用户运行 Codex 的电脑能访问网站即可。云端执行环境必须另外具备到内网的网络路径。

## 创建访问令牌

1. 登录网站，进入「个人主页 → MCP 访问」。
2. 填写名称，选择有效期和读取、修改投递/日程的权限。修改权限同时包含对应读取权限。
3. 点击「创建访问令牌」，复制令牌或完整 Codex 配置。令牌只显示一次，离开个人主页后会清除当前页面中的完整令牌。
4. 同一账户可以创建多个令牌，每个设备使用独立令牌，最多保留 20 个有效令牌。

服务端保存 SHA-256 摘要和显示前缀，不保存完整令牌。令牌与网站密码、网页登录 Cookie、大模型 API Key 相互独立。令牌过期、撤销、账户被停用或删除后不能继续访问；每次调用都会校验。网站显示有效期、最近使用时间和最近 20 次工具操作。每个账户的工具审计最多保留 1000 条，不保存令牌、备注或面试回顾正文。

## 配置 Codex

在 Codex 的 MCP 设置中打开配置文件，或编辑本机 `~/.codex/config.toml`。以当前内网地址为例：

```toml
[mcp_servers.careerflow]
url = "http://10.12.152.159:18080/mcp"
http_headers = { Authorization = "Bearer 替换为你的个人访问令牌" }
```

网站提供的「复制 Codex 配置」使用当前访问地址，并填入刚创建的令牌。若已有 `[mcp_servers.careerflow]`，替换该段，不要重复添加。保存后重启 Codex，在 MCP 服务器列表中检查连接。配置包含账户访问凭证，只保存在可信设备，不要提交到 Git 仓库。

也可以把令牌放在环境变量中，避免直接写入 TOML：

```toml
[mcp_servers.careerflow]
url = "http://10.12.152.159:18080/mcp"
bearer_token_env_var = "CAREERFLOW_MCP_TOKEN"
```

Windows PowerShell 中为当前用户设置该变量后，完全退出并重新打开 Codex，使其继承新环境变量：

```powershell
[Environment]::SetEnvironmentVariable('CAREERFLOW_MCP_TOKEN', '替换为你的个人访问令牌', 'User')
```

本地 CLI 支持：

```text
codex mcp add careerflow --url http://10.12.152.159:18080/mcp --bearer-token-env-var CAREERFLOW_MCP_TOKEN
```

服务地址随服务器地址变化时，在配置中修改 URL。HTTP 内网部署不加密访问令牌和业务内容，适用于受信任网络；若需要在不受信任网络使用，应由部署者配置 HTTPS。

## 可用操作

| 工具 | 权限 | 用途 |
| --- | --- | --- |
| `search_applications` | applications:read | 按关键词、阶段分页查询投递 |
| `get_application` | applications:read | 获取投递详情与记录版本 |
| `create_application` | applications:write | 新增投递 |
| `update_application` | applications:write | 修改明确提供的投递字段 |
| `list_events` | events:read | 按结束/发生时间查询日程，支持状态、投递和时间范围筛选 |
| `get_event` | events:read | 获取日程备注、面试回顾和版本 |
| `create_event` | events:write | 为自己的投递新增日程 |
| `update_event` | events:write | 修改指定日程的字段、备注和回顾 |
| `resolve_event` | events:write | 完成、错过、放弃或恢复日程 |

工具只暴露令牌允许的操作，直接调用未授权工具同样会被拒绝。工具不能选择其他账户，管理员令牌也只能操作管理员自己的投递和日程。本版不提供删除、批量修改、管理员操作、密钥读取或邮件服务器访问。

时间使用北京时间（Asia/Shanghai）`YYYY-MM-DD HH:mm`。时间点的 `endsAt` 留空，时间段的 `endsAt` 必须晚于 `startsAt`。日程列表按时间点的 `startsAt`、时间段的 `endsAt` 排序和筛选。列表默认每页 20 条、最多 50 条，不返回长备注和面试回顾，需要时使用详情工具。

修改前先查询记录的 `id` 和 `updatedAt`，将其作为 `expectedUpdatedAt` 传入。网页和 Codex 同时修改同一条记录会报告冲突，必须重新查询后核对修改。未提供的字段保留原值，岗位备注和日程备注分别更新。只有已完成且未标为错过或放弃的日程允许填写面试回顾；需要时先按用户要求标记完成，再使用返回的新版本保存回顾。

MCP 工具不需要调用站内大模型。`create_application` 的 `generateCompanyInsights` 默认 `false`；设置为 `true` 会触发站内公司的分类与薪资估算并产生相应 API 用量。之后进入网站相关页面，网站原有的异步补全机制仍可能运行。保存面试回顾不会通过 MCP 自动执行全量考点重建。

## 常见问题

- **401**：检查是否使用 `Authorization: Bearer ...`，令牌是否过期或被撤销。网页登录 Cookie 不用于 MCP。
- **403**：请求携带了不符合服务地址的 Origin；本服务不允许任意跨站网页调用。工具内的权限不足会返回 `permission_denied`。
- **429**：每个令牌每分钟最多 120 次 HTTP 调用，等待后重试。
- **conflict**：记录已更新，重新获取详情和 `updatedAt`。
- **无法连接**：先确认运行 Codex 的电脑能打开网站；同名 Wi-Fi 的访客网络或设备隔离也可能阻止访问。
- **GET /mcp 返回 405**：正常。本服务为无状态 HTTP MCP，只通过 POST 返回 JSON，不创建独立 SSE 流。

## 协议与维护

支持 MCP 协议版本 `2025-03-26`、`2025-06-18` 和 `2025-11-25` 的基础工具能力，未知初始化版本协商为 `2025-06-18`。支持 `initialize`、`ping`、`tools/list`、`tools/call` 和初始化通知；不提供 resources/prompts、服务端推送或 MCP 会话恢复。JSON-RPC 单条消息上限为 64KB。新增数据库表 `mcp_tokens` 和 `mcp_audit_logs`，SQLite/PostgreSQL 均由启动初始化创建，既有业务表无需变更。

- [Codex MCP 官方说明](https://learn.chatgpt.com/docs/extend/mcp)
- [MCP Streamable HTTP 协议](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)
