#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
image="${1:?image tag required}"
image_id="${2:?image ID required}"
archive_hash="${3:?archive SHA256 required}"
[[ "$image" =~ ^careerflow:[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]]
[[ "$image_id" =~ ^sha256:[a-f0-9]{64}$ && "$archive_hash" =~ ^[a-f0-9]{64}$ ]]
exec 9>run-docker-deploy.lock
flock -n 9 || { echo 'Another Docker deployment is in progress.' >&2; exit 1; }
docker info >/dev/null
command -v python3 >/dev/null
test -f .env
test -f data/jobtracker.db
test -d backups
test -x stop.sh
test -x start.sh
printf '%s  %s\n' "$archive_hash" careerflow-image.tar.uploading | sha256sum --check --status
docker load --input careerflow-image.tar.uploading
[[ "$(docker image inspect --format '{{.Id}}' "$image")" == "$image_id" ]]

# The existing deployment owns this trusted shell file. Never print its contents.
set -a
source .env
set +a
export CAREERFLOW_IMAGE="$image" APP_UID="$(id -u)" APP_GID="$(id -g)"
export APP_PORT="${PORT:-${APP_PORT:-18080}}"
[[ "$APP_PORT" =~ ^[0-9]+$ ]] && (( APP_PORT >= 1024 && APP_PORT <= 65535 ))
for name in SESSION_SECRET ENCRYPTION_KEY ADMIN_EMAIL; do
  [[ -n "${!name:-}" ]] || { echo "Missing setting: $name" >&2; exit 1; }
done
python3 - <<'PY'
import os, pathlib, re
root = pathlib.Path.cwd()
url = os.environ.get('APP_DATABASE_URL', '')
if not url.startswith('jdbc:sqlite:'):
    raise SystemExit('Automatic migration requires the existing SQLite database.')
if pathlib.Path(url[len('jdbc:sqlite:'):]).resolve() != (root / 'data/jobtracker.db').resolve():
    raise SystemExit('Database path differs from data/jobtracker.db; refusing to start with an empty database.')
names = set(re.findall(r'^\s*(?:export\s+)?([A-Z][A-Z0-9_]*)=', (root / '.env').read_text(), re.M))
names.update(['JAVA_TOOL_OPTIONS'])
values = {name: os.environ[name] for name in sorted(names) if name in os.environ}
values['JAVA_TOOL_OPTIONS'] = '-Xms' + os.environ.get('JAVA_XMS', '256m') + ' -Xmx' + os.environ.get('JAVA_XMX', '1024m') + ' -Duser.timezone=Asia/Shanghai'
for value in values.values():
    if '\n' in value or '\r' in value:
        raise SystemExit('Multiline environment values require manual migration.')
path = root / '.env.docker'
path.write_text(''.join(name + '=' + value + '\n' for name, value in values.items()))
path.chmod(0o600)
path = root / '.docker-release.next.env'
path.write_text(''.join(name + '=' + os.environ[name] + '\n' for name in ['CAREERFLOW_IMAGE', 'APP_PORT', 'APP_UID', 'APP_GID']))
PY
unset CAREERFLOW_IMAGE APP_UID APP_GID APP_PORT
# Validate the candidate without exposing rendered configuration/secrets in logs.
docker compose -p careerflow --env-file .docker-release.next.env -f docker-compose.server.next.yml config --quiet
existing_docker=false
if [[ -f .docker-release.env && -f docker-compose.server.yml ]]; then
  existing_docker=true
  cp .docker-release.env .docker-release.previous.env
  cp docker-compose.server.yml docker-compose.server.previous.yml
fi
changed=false
rollback() {
  result=$?
  trap - ERR
  if [[ "$changed" == true ]]; then
    echo 'Container deployment failed; restoring previous runtime.' >&2
    docker compose -p careerflow --env-file .docker-release.env -f docker-compose.server.yml down || true
    if [[ "$existing_docker" == true ]]; then
      cp .docker-release.previous.env .docker-release.env
      cp docker-compose.server.previous.yml docker-compose.server.yml
      docker compose -p careerflow --env-file .docker-release.env -f docker-compose.server.yml up -d --wait --wait-timeout 120 || true
    else
      rm -f .docker-release.env
      ./start.sh || true
    fi
  fi
  exit "$result"
}
trap rollback ERR
if [[ "$existing_docker" == true ]]; then
  docker compose -p careerflow --env-file .docker-release.env -f docker-compose.server.yml stop
else
  ./stop.sh
fi
changed=true
# Back up SQLite only after the previous writer has stopped. Keep this snapshot.
python3 - <<'PY'
import datetime, pathlib, sqlite3
folder = pathlib.Path('backups/docker-migration')
folder.mkdir(parents=True, exist_ok=True)
target = folder / (datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '.db')
with sqlite3.connect('file:data/jobtracker.db?mode=ro', uri=True) as source, sqlite3.connect(str(target)) as dest:
    source.backup(dest)
    if dest.execute('PRAGMA quick_check').fetchone()[0] != 'ok':
        raise RuntimeError('SQLite backup integrity check failed')
PY
cp .docker-release.next.env .docker-release.env
cp docker-compose.server.next.yml docker-compose.server.yml
docker compose -p careerflow --env-file .docker-release.env -f docker-compose.server.yml up -d --wait --wait-timeout 120
docker compose -p careerflow --env-file .docker-release.env -f docker-compose.server.yml exec -T app curl -fsS http://127.0.0.1:8080/healthz
trap - ERR
rm -f careerflow-image.tar.uploading .docker-release.next.env
echo
echo 'Docker deployment healthy. Existing data, secrets and JAR rollback files are preserved.'
