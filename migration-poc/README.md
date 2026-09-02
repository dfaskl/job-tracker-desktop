# Vue + Java 迁移验证原型

这个目录用于验证现有 Node.js 在线版能否无损迁移到 Vue 3 + Spring Boot。它不会修改原系统代码，也不会写入数据库。

## 已覆盖的兼容点

- 使用与 Node.js crypto.scrypt 相同的参数验证旧密码哈希。
- 使用现有 AES-256-GCM 字段格式解密旧 API Key 密文。
- 接受现有 postgresql:// 格式的 DATABASE_URL。
- 只读检查八张现有表，以及 user_data.data 中的 applications 和 events 数组。
- 将 Vue 构建结果打进一个可执行 Spring Boot JAR。

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

不要把生产数据库连接串、加密主密钥或访问令牌写入仓库。
