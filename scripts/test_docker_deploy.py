"""Linux integration checks for deployment orchestration, using a fake Docker CLI.

python3 scripts/test_docker_deploy.py deploy/docker/deploy.sh
No real Docker engine, production configuration or network is used.
"""
import hashlib
import os
import pathlib
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(sys.argv.pop(1)).resolve()
IMAGE_ID = 'sha256:' + 'a' * 64


class DockerDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='careerflow-docker-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        for folder in ['data', 'backups', 'bin']:
            (self.root / folder).mkdir()
        shutil.copyfile(SCRIPT, self.root / 'deploy-docker.next.sh')
        self.write('docker-compose.server.next.yml', 'services: {}\n')
        self.write('careerflow-image.tar.uploading', 'fake image archive')
        self.checksum = hashlib.sha256(b'fake image archive').hexdigest()
        with sqlite3.connect(str(self.root / 'data/jobtracker.db')) as db:
            db.execute('create table sentinel (value text)')
            db.execute("insert into sentinel values ('existing data')")
        self.write('.env', "APP_DATABASE_URL='jdbc:sqlite:" + str(self.root / 'data/jobtracker.db') + "'\n"
                   "APP_PORT=18080\nSESSION_SECRET='keep$literal#secret'\n"
                   "ENCRYPTION_KEY='keep-encryption'\nADMIN_EMAIL='test@example.com'\n")
        self.write('stop.sh', '#!/bin/bash\ntouch legacy-stopped\n', executable=True)
        self.write('start.sh', '#!/bin/bash\ntouch legacy-restarted\n', executable=True)
        self.write('bin/docker', '''#!/usr/bin/env python3
import json, os, pathlib, sys
r=pathlib.Path.cwd(); args=sys.argv[1:]
with (r/'docker-calls').open('a') as f: f.write(json.dumps(args)+'\\n')
if args[0]=='info' and os.environ.get('FAKE_DENIED'): sys.exit(1)
if args[:2]==['image','inspect']: print('sha256:'+'a'*64)
if 'config' in args and os.environ.get('FAKE_CONFIG_FAIL'): sys.exit(1)
if 'up' in args and os.environ.get('FAKE_UP_FAIL') and not (r/'up-failed').exists():
    (r/'up-failed').touch(); sys.exit(1)
''', executable=True)

    def write(self, name, value, executable=False):
        p = self.root / name
        p.write_text(value)
        p.chmod(0o700 if executable else 0o600)

    def run_deploy(self, **extra):
        env = dict(os.environ, PATH=str(self.root / 'bin') + ':' + os.environ['PATH'], **extra)
        # Avoid leaking the test runner's release configuration into Compose.
        for key in ['CAREERFLOW_IMAGE', 'APP_UID', 'APP_GID', 'APP_PORT', 'PORT']:
            env.pop(key, None)
        return subprocess.run(['bash', 'deploy-docker.next.sh', 'careerflow:test', IMAGE_ID, self.checksum],
                              cwd=str(self.root), env=env, capture_output=True, text=True)

    def test_migrates_preserving_database_and_literal_secrets(self):
        result = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.root / 'legacy-stopped').exists())
        self.assertFalse((self.root / 'legacy-restarted').exists())
        self.assertIn('SESSION_SECRET=keep$literal#secret\n', (self.root / '.env.docker').read_text())
        self.assertEqual((self.root / '.env.docker').stat().st_mode & 0o777, 0o600)
        self.assertNotIn('keep$literal#secret', result.stdout + result.stderr)
        snapshots = list((self.root / 'backups/docker-migration').glob('*.db'))
        self.assertEqual(len(snapshots), 1)
        with sqlite3.connect(str(snapshots[0])) as db:
            self.assertEqual(db.execute('select value from sentinel').fetchone()[0], 'existing data')

    def test_permission_failure_does_not_stop_service(self):
        result = self.run_deploy(FAKE_DENIED='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / 'legacy-stopped').exists())

    def test_checksum_failure_does_not_stop_service(self):
        self.checksum = '0' * 64
        result = self.run_deploy()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / 'legacy-stopped').exists())

    def test_config_failure_does_not_stop_service(self):
        result = self.run_deploy(FAKE_CONFIG_FAIL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / 'legacy-stopped').exists())

    def test_rejects_database_path_mismatch(self):
        path = self.root / '.env'
        path.write_text(path.read_text().replace('data/jobtracker.db', 'data/other.db'))
        result = self.run_deploy()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / 'legacy-stopped').exists())

    def test_failed_first_start_restores_jar_and_allows_retry(self):
        result = self.run_deploy(FAKE_UP_FAIL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.root / 'legacy-restarted').exists())
        self.assertFalse((self.root / '.docker-release.env').exists())
        result = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_failed_update_restores_previous_container_release(self):
        self.write('.docker-release.env', 'CAREERFLOW_IMAGE=careerflow:old\n')
        self.write('docker-compose.server.yml', '# original compose\n')
        result = self.run_deploy(FAKE_UP_FAIL='1')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('careerflow:old', (self.root / '.docker-release.env').read_text())
        self.assertEqual((self.root / 'docker-compose.server.yml').read_text(), '# original compose\n')
        self.assertFalse((self.root / 'legacy-restarted').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
