# 求职进度本

仓库现在只以 Vue 3 + Spring Boot 新版系统作为开发、构建和部署入口。

## 当前正式系统

代码目录：[`migration-poc/`](migration-poc/)

自有服务器部署、Docker Compose、环境变量和本地构建方法见 [`migration-poc/README.md`](migration-poc/README.md)。

## 旧版归档

旧 Node.js 服务、静态前端、Windows 启动脚本及旧测试已经移入 [`deprecated-legacy-system/`](deprecated-legacy-system/)，并标记为已废弃。该目录只用于历史追溯，不参与当前构建和部署。

仓库根目录的 `data/` 可能包含旧版的本地业务数据，为避免数据损失，它被原地保留且继续由 Git 忽略，不属于当前应用代码。