#!/usr/bin/env sh
set -eu

health_url="${HEALTH_URL:-https://job-tracker-migration-poc.onrender.com/healthz}"
interval_seconds="${INTERVAL_SECONDS:-600}"
request_timeout_seconds="${REQUEST_TIMEOUT_SECONDS:-30}"

case "$interval_seconds" in
  ''|*[!0-9]*) echo "INTERVAL_SECONDS must be a positive integer." >&2; exit 1 ;;
esac
case "$request_timeout_seconds" in
  ''|*[!0-9]*) echo "REQUEST_TIMEOUT_SECONDS must be a positive integer." >&2; exit 1 ;;
esac

[ "$interval_seconds" -gt 0 ] || { echo "INTERVAL_SECONDS must be greater than zero." >&2; exit 1; }
[ "$request_timeout_seconds" -gt 0 ] || { echo "REQUEST_TIMEOUT_SECONDS must be greater than zero." >&2; exit 1; }

echo "Keeping $health_url awake every $interval_seconds seconds. Press Ctrl+C to stop."

while :; do
  if ! curl --fail --silent --show-error \
      --max-time "$request_timeout_seconds" \
      --output /dev/null \
      "$health_url"; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') health check failed; retrying after $interval_seconds seconds." >&2
  fi
  sleep "$interval_seconds"
done