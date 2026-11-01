"""Create a private, consistent recovery snapshot. Only metadata is printed."""
import argparse
from contextlib import closing
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import sqlite3
import tarfile
import time


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def database_path(root):
    for line in (root / '.env').read_text().splitlines():
        match = re.match(r'^\s*(?:export\s+)?APP_DATABASE_URL\s*=\s*(.*)$', line)
        if not match:
            continue
        parts = shlex.split(match.group(1), comments=True)
        if len(parts) != 1 or not parts[0].startswith('jdbc:sqlite:'):
            raise ValueError('A literal SQLite APP_DATABASE_URL is required for this backup.')
        value = parts[0][len('jdbc:sqlite:'):]
        path = Path(value)
        if not path.is_absolute():
            path = root / path
        path = path.resolve()
        if path != (root / 'data/jobtracker.db').resolve() or not path.is_file():
            raise ValueError('Database is not the expected data/jobtracker.db; refusing an incomplete backup.')
        return path
    raise ValueError('APP_DATABASE_URL is missing from the server configuration.')


def snapshot(root, token, kind):
    folder = root / 'run' / ('local-backup-' + token)
    folder.mkdir(mode=0o700)
    try:
        payload = folder / 'payload'
        (payload / 'data').mkdir(parents=True, mode=0o700)
        source_path = database_path(root)
        deadline = time.monotonic() + 180

        def progress(status, remaining, total):
            if time.monotonic() > deadline:
                raise TimeoutError('SQLite snapshot timed out; previous local backup is retained.')

        with closing(sqlite3.connect(source_path.as_uri() + '?mode=ro', uri=True, timeout=30)) as source, \
                closing(sqlite3.connect(str(payload / 'data/jobtracker.db'))) as target:
            source.backup(target, pages=512, progress=progress, sleep=0.05)
            checks = target.execute('PRAGMA quick_check').fetchall()
            if checks != [('ok',)]:
                raise ValueError('SQLite snapshot integrity check failed.')
        shutil.copy2(root / '.env', payload / '.env')
        if kind == 'full':
            for name in ['job-tracker.jar', 'start.sh', 'stop.sh', 'status.sh']:
                shutil.copy2(root / name, payload / name)
            for name in ['env.example', 'migrate-to-sqlite.sh']:
                if (root / name).is_file():
                    shutil.copy2(root / name, payload / name)
            if (root / 'logs/app.log').is_file():
                (payload / 'logs').mkdir()
                shutil.copyfile(root / 'logs/app.log', payload / 'logs/app.log')
            if not (root / 'runtime/bin/java').is_file():
                raise ValueError('The deployment Java runtime is missing.')
            with tarfile.open(payload / 'runtime.tar.gz', 'w:gz') as archive:
                archive.add(root / 'runtime', arcname='runtime')
        files = {str(p.relative_to(payload)).replace(os.sep, '/'): sha256(p)
                 for p in payload.rglob('*') if p.is_file()}
        manifest = {
            'formatVersion': 1, 'kind': kind,
            'createdAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'jarSha256': sha256(root / 'job-tracker.jar'), 'files': files,
        }
        (payload / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
        archive_path = folder / 'snapshot.tar.gz'
        with tarfile.open(archive_path, 'w:gz') as archive:
            for path in sorted(payload.rglob('*')):
                if path.is_file():
                    archive.add(path, arcname=str(path.relative_to(payload)), recursive=False)
        shutil.rmtree(payload)
        print(json.dumps({'sha256': sha256(archive_path), 'size': archive_path.stat().st_size}))
    except BaseException:
        shutil.rmtree(folder)
        raise


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', required=True)
    parser.add_argument('--token', required=True)
    parser.add_argument('--kind', choices=['full', 'database', 'cleanup'], required=True)
    args = parser.parse_args()
    if not re.fullmatch('[a-f0-9]{32}', args.token):
        raise ValueError('Invalid snapshot token.')
    root = Path(args.root).resolve(strict=True)
    if args.kind == 'cleanup':
        folder = root / 'run' / ('local-backup-' + args.token)
        if folder.exists():
            shutil.rmtree(folder)
        return
    os.umask(0o077)
    # The JAR deployment script uses the same lock while swapping/restarting JARs.
    import fcntl
    with (root / 'run' / 'deployment-backup.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        snapshot(root, args.token, args.kind)


if __name__ == '__main__':
    main()
