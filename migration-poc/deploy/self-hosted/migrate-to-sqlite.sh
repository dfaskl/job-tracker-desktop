#!/usr/bin/env bash
set -Eeuo pipefail

APP_HOME="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$APP_HOME"

[[ -f .env ]] || { echo "缺少 $APP_HOME/.env" >&2; exit 1; }
[[ -x runtime/bin/java ]] || { echo "缺少内置 Java：$APP_HOME/runtime/bin/java" >&2; exit 1; }
[[ -f job-tracker.jar ]] || { echo "缺少应用文件：$APP_HOME/job-tracker.jar" >&2; exit 1; }

set -a
source "$APP_HOME/.env"
set +a

source_url="${APP_DATABASE_URL:-}"
[[ -n "$source_url" ]] || { echo ".env 中缺少 APP_DATABASE_URL" >&2; exit 1; }
if [[ "$source_url" == sqlite:* || "$source_url" == jdbc:sqlite:* ]]; then
  echo "当前配置已经使用 SQLite，无需再次迁移。"
  exit 0
fi

mkdir -p "$APP_HOME/data" "$APP_HOME/backups" "$APP_HOME/logs" "$APP_HOME/run"
chmod 700 "$APP_HOME/data" "$APP_HOME/backups"
target_file="$APP_HOME/data/jobtracker.db"
target_url="jdbc:sqlite:$target_file"
if [[ -e "$target_file" ]]; then
  echo "目标文件已存在，为避免覆盖数据已停止：$target_file" >&2
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
env_backup="$APP_HOME/backups/env-before-sqlite-$timestamp"
migration_log="$APP_HOME/logs/sqlite-migration-$timestamp.log"
cp -p "$APP_HOME/.env" "$env_backup"
chmod 600 "$env_backup"

was_running=false
if [[ -f "$APP_HOME/run/app.pid" ]]; then
  current_pid="$(cat "$APP_HOME/run/app.pid")"
  if [[ "$current_pid" =~ ^[0-9]+$ ]] && kill -0 "$current_pid" 2>/dev/null; then
    was_running=true
  fi
fi

if [[ "$was_running" == true ]]; then
  "$APP_HOME/stop.sh"
fi

echo "正在将 Neon PostgreSQL 数据复制到 SQLite，请不要关闭终端……"
if ! env \
  APP_DATABASE_URL="$target_url" \
  MIGRATION_SOURCE_DATABASE_URL="$source_url" \
  SQLITE_MIGRATION_ENABLED=true \
  SQLITE_MIGRATION_EXIT=true \
  SQLITE_BACKUP_ENABLED=false \
  MAIL_SYNC_INITIAL_DELAY_MS=86400000 \
  SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  "$APP_HOME/runtime/bin/java" \
  "-Xms${JAVA_XMS:-256m}" \
  "-Xmx${JAVA_XMX:-1024m}" \
  -Duser.timezone=Asia/Shanghai \
  -jar "$APP_HOME/job-tracker.jar" \
  >"$migration_log" 2>&1; then
  if [[ -e "$target_file" ]]; then
    mv "$target_file" "$APP_HOME/backups/jobtracker-failed-$timestamp.db"
  fi
  echo "迁移失败，原 .env 未修改。日志：$migration_log" >&2
  if [[ "$was_running" == true ]]; then "$APP_HOME/start.sh" || true; fi
  exit 1
fi

replacement="APP_DATABASE_URL='$target_url'"
awk -v replacement="$replacement" '
  BEGIN { replaced=0 }
  /^APP_DATABASE_URL=/ { print replacement; replaced=1; next }
  { print }
  END { if (!replaced) print replacement }
' "$APP_HOME/.env" > "$APP_HOME/.env.sqlite.tmp"
chmod 600 "$APP_HOME/.env.sqlite.tmp"
mv "$APP_HOME/.env.sqlite.tmp" "$APP_HOME/.env"

if ! "$APP_HOME/start.sh"; then
  cp -p "$env_backup" "$APP_HOME/.env"
  mv "$target_file" "$APP_HOME/backups/jobtracker-start-failed-$timestamp.db"
  echo "SQLite 应用启动失败，已恢复 Neon 配置并尝试重启。" >&2
  "$APP_HOME/start.sh" || true
  exit 1
fi

echo "SQLite 迁移完成。数据库：$target_file"
echo "原配置备份：$env_backup"
echo "迁移日志：$migration_log"
