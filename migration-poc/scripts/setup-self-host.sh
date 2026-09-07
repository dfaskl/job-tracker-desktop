#!/usr/bin/env sh
set -eu

if [ -f .env ]; then
  echo ".env already exists; keeping the existing configuration."
else
  admin_email="${1:-}"
  if [ -z "$admin_email" ]; then
    printf "Administrator email: "
    read -r admin_email
  fi
  [ -n "$admin_email" ] || { echo "Administrator email is required." >&2; exit 1; }
  random_secret() { openssl rand -hex 32; }
  umask 077
  cat > .env <<EOF
ADMIN_EMAIL=$admin_email
APP_PORT=8080
POSTGRES_PASSWORD=$(random_secret)
SESSION_SECRET=$(random_secret)
ENCRYPTION_KEY=$(random_secret)
ALLOW_REGISTRATION=true
REGISTRATION_CODE=
SESSION_DAYS=7
AI_CALLS_ENABLED=true
AI_ALLOWED_HOSTS=api.openai.com,api.deepseek.com
EOF
  echo "Created .env with generated secrets."
fi

docker compose up -d --build