#!/usr/bin/env bash
set -Eeuo pipefail

APP_HOME="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
pid_file="$APP_HOME/run/app.pid"

if [[ ! -f "$pid_file" ]]; then
  echo "应用未运行（未找到 PID 文件）。"
  exit 0
fi

pid="$(cat "$pid_file")"
if [[ ! "$pid" =~ ^[0-9]+$ ]] || ! kill -0 "$pid" 2>/dev/null; then
  echo "应用未运行，清理过期 PID 文件。"
  rm -f "$pid_file"
  exit 0
fi

kill "$pid"
for _ in $(seq 1 30); do
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$pid_file"
    echo "应用已停止。"
    exit 0
  fi
  sleep 1
done

echo "应用未能在 30 秒内退出，执行强制停止。" >&2
kill -KILL "$pid"
rm -f "$pid_file"

