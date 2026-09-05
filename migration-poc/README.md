# Vue + Java 迁移验证原型

这个目录用于验证现有 Node.js 在线版能否无损迁移到 Vue 3 + Spring Boot。Vue 页面和 Java API 最终打入一个可执行 JAR，当前公开 Demo 与旧服务并行运行。

生产数据库始终只读。职位、日程、会话、备份恢复、AI 配置和管理员操作虽然已有迁移实现，但必须显式连接独立测试库并开启对应开关；默认全部关闭。

## 已覆盖的兼容点

- 使用与 Node.js crypto.scrypt 相同的参数验证旧密码哈希。
- 使用现有 AES-256-GCM 字段格式解密旧 API Key 密文。
- 接受现有 postgresql:// 格式的 DATABASE_URL。
- 只读检查八张现有表，以及 user_data.data 中的 applications 和 events 数组。
- 将 Vue 构建结果打进一个可执行 Spring Boot JAR。
- 在独立测试库中验证职位 CRUD、日程、备份恢复和持久会话。
- 兼容加密保存 AI 配置，并提供受 HTTPS、域名白名单和响应大小限制保护的邮件识别。
- 迁移管理员概览、注册开关、普通用户启停/删除、流程查看和审计日志。

详细安全边界见 `CRUD-SANDBOX.md`、`EVENT-SANDBOX.md`、`PERSISTENT-SESSIONS.md`、`BACKUP-RESTORE.md`、`AI-MAIL-MIGRATION.md`、`ADMIN-SANDBOX.md`。最终验收和切换步骤见 `MIGRATION-READINESS.md`。

## 构建

要求 Java 21+、Maven 3.9+、Node.js 22.18+。

    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
    mvn -f backend\pom.xml clean package

生成文件：backend/target/job-tracker-migration-poc.jar。

## 运行

不连接数据库也可以启动和查看兼容性页面：

    java -jar backend\target\job-tracker-migration-poc.jar

访问 http://127.0.0.1:8080。

若要对隔离的测试数据库执行只读检查，请在进程环境中提供：

- DATABASE_URL
- POC_ACCESS_TOKEN

页面输入 POC_ACCESS_TOKEN 后才会触发数据库读取。原型只执行 SELECT，不会返回业务数据内容。

若要做独立测试库演练，再按具体阶段设置：

- `POC_WRITE_DATABASE_URL` 与 `POC_WRITE_ENABLED=true`
- `POC_PERSISTENT_SESSION_ENABLED=true`
- `POC_ENCRYPTION_KEY` 与 `POC_AI_CALLS_ENABLED=true`
- `POC_ADMIN_ENABLED=true`

写入沙箱会拒绝与 `DATABASE_URL` 指向同一数据库的地址。公开 Render 配置不包含这些变量。

不要把生产数据库连接串、加密主密钥或访问令牌写入仓库。

## 新旧系统并行正式使用

新系统支持独立数据库和与旧系统共享生产数据库两种写入模式。共享模式必须同时满足以下条件：

- `DATABASE_URL`：旧系统正在使用的 PostgreSQL 地址。
- `POC_WRITE_DATABASE_URL`：填写与 `DATABASE_URL` 相同的地址。
- `POC_WRITE_ENABLED=true`：开启业务写入。
- `POC_SHARED_DATABASE_WRITE_ENABLED=true`：第二重确认，允许共享生产数据。
- `POC_PERSISTENT_SESSION_ENABLED=true`：使用数据库会话。
- `POC_ENCRYPTION_KEY`：与旧系统 `ENCRYPTION_KEY` 相同，以兼容已有 AI 密钥。
- `POC_AI_CALLS_ENABLED=true`：按需开启 AI 邮件识别与字段规范化。
- `POC_ADMIN_ENABLED=true`：开启管理员功能（正式部署默认开启）。

开启后，新旧系统读取同一组用户、投递、日程、公司链接、AI 配置和备份。每次业务写入前都会生成备份；同一账号不要在两个页面中同时编辑同一条记录，遇到版本冲突时刷新后重试。
