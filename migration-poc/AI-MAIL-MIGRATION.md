# AI 配置、密钥与邮件识别迁移

第 5 阶段只在独立测试数据库中保存 AI 配置。生产 `api_configs` 不会被修改；外部 AI 请求还需要独立设置 `POC_AI_CALLS_ENABLED=true`。

## 密钥兼容

- 使用独立环境变量 `POC_ENCRYPTION_KEY`，避免原型默认读取生产加密主密钥。
- 密钥解析与旧 Node.js 相同：64 位十六进制、32 字节 Base64，或对至少 32 字符文本执行 SHA-256。
- API Key 使用 AES-256-GCM、12 字节 IV 和 16 字节认证标签，分别保存到旧字段 `encrypted_api_key`、`encryption_iv`、`auth_tag`。
- 查询配置只返回 `hasApiKey` 和末四位，不返回或记录明文。

若测试库复制了旧密文，需要将原系统相同的加密主密钥安全配置为 `POC_ENCRYPTION_KEY`，Java 才能解密并调用 API。不要把该值写入仓库。

## 外部请求安全

- 只接受 HTTPS。
- 默认仅允许 `api.deepseek.com` 和 `api.openai.com`；可用 `AI_ALLOWED_HOSTS` 配置明确白名单。
- 拒绝 URL 用户信息、localhost、`.local`、环回、链路本地、私网、组播和 IPv6 唯一本地地址。
- 不跟随 HTTP 重定向，连接超时 10 秒、请求超时 60 秒、响应上限 1 MiB。
- 同一账号两次 AI 请求至少间隔 3 秒。

## 邮件识别

- 邮件正文最多 100000 个字符，并在系统提示中明确作为不可信数据处理。
- 只返回公司、岗位、通知类型、建议阶段/状态、时间和地点。
- `summary` 强制为空，避免复制邮件中的密码、联系人或其他敏感正文。
- 时间段只有在开始和结束均有效且结束晚于开始时才保留，否则降级为时间点或空时间。
- 识别结果不会自动写入职位或日程，需要用户人工核对。

接口：

- `GET /api/poc/ai-sandbox/status`
- `GET /api/poc/ai-sandbox/config`
- `POST /api/poc/ai-sandbox/config`
- `POST /api/poc/ai-sandbox/recognize`
