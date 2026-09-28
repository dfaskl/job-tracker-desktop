#!/usr/bin/env bash
set -Eeuo pipefail

APP_HOME="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
pid_file="$APP_HOME/run/app.pid"

if [[ -f "$pid_file" ]]; then
  pid="$(cat "$pid_file")"
  if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
    echo "应用正在运行，PID=$pid"
    exit 0
  fi
fi

echo "应用未运行。"
exit 1

