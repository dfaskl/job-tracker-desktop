#!/usr/bin/env bash
set -Eeuo pipefail

APP_HOME="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$APP_HOME"

if [[ ! -f .env ]]; then
  echo "缺少 $APP_HOME/.env；请先复制 env.example 并填写配置。" >&2
  exit 1
fi

set -a
# .env 由部署者维护，按受信任的 shell 环境变量文件加载。
source "$APP_HOME/.env"
set +a

required=(APP_DATABASE_URL SESSION_SECRET ENCRYPTION_KEY ADMIN_EMAIL)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo ".env 中缺少必填项：$name" >&2
    exit 1
  fi
done

effective_port="${PORT:-${APP_PORT:-18080}}"
if [[ ! "$effective_port" =~ ^[0-9]+$ ]] || (( effective_port < 1024 || effective_port > 65535 )); then
  echo "端口必须是 1024-65535 之间的整数，当前值：$effective_port" >&2
  exit 1
fi

mkdir -p "$APP_HOME/logs" "$APP_HOME/run" "$APP_HOME/data" "$APP_HOME/backups"
chmod 700 "$APP_HOME/data" "$APP_HOME/backups"
pid_file="$APP_HOME/run/app.pid"

if [[ -f "$pid_file" ]]; then
  old_pid="$(cat "$pid_file")"
  if [[ "$old_pid" =~ ^[0-9]+$ ]] && kill -0 "$old_pid" 2>/dev/null; then
    echo "应用已经运行，PID=$old_pid"
    exit 0
  fi
  rm -f "$pid_file"
fi

if command -v ss >/dev/null 2>&1 && [[ -n "$(ss -H -ltn "sport = :$effective_port" 2>/dev/null)" ]]; then
  echo "端口 $effective_port 已被其他进程占用，请修改 .env 中的 APP_PORT。" >&2
  exit 1
fi

java_bin="$APP_HOME/runtime/bin/java"
jar_file="$APP_HOME/job-tracker.jar"
[[ -x "$java_bin" ]] || { echo "缺少可执行的内置 Java：$java_bin" >&2; exit 1; }
[[ -f "$jar_file" ]] || { echo "缺少应用文件：$jar_file" >&2; exit 1; }

java_xms="${JAVA_XMS:-256m}"
java_xmx="${JAVA_XMX:-1024m}"
log_file="$APP_HOME/logs/app.log"

nohup "$java_bin" \
  "-Xms$java_xms" \
  "-Xmx$java_xmx" \
  -Duser.timezone=Asia/Shanghai \
  -jar "$jar_file" \
  >>"$log_file" 2>&1 &

app_pid=$!
printf '%s\n' "$app_pid" > "$pid_file"

for _ in $(seq 1 30); do
  if ! kill -0 "$app_pid" 2>/dev/null; then
    echo "应用启动失败，最近日志如下：" >&2
    tail -n 80 "$log_file" >&2 || true
    rm -f "$pid_file"
    exit 1
  fi
  if curl --silent --fail --max-time 2 "http://127.0.0.1:$effective_port/healthz" >/dev/null 2>&1; then
    echo "应用已启动：http://127.0.0.1:$effective_port（PID=$app_pid）"
    exit 0
  fi
  sleep 1
done

echo "进程仍在运行，但健康检查在 30 秒内未通过。请查看：$log_file" >&2
exit 1
