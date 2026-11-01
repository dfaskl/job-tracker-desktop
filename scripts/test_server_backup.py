"""Linux tests for consistent snapshots, without using production files."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sqlite3
import sys
import tarfile
import tempfile
import unittest

helper_path = Path(sys.argv.pop(1)).resolve()
spec = importlib.util.spec_from_file_location('server_backup', helper_path)
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)


class SnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='careerflow-backup-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in ['data', 'run', 'runtime/bin', 'logs']:
            (self.root / name).mkdir(parents=True, exist_ok=True)
        for name in ['job-tracker.jar', 'start.sh', 'stop.sh', 'status.sh', 'runtime/bin/java', 'logs/app.log']:
            (self.root / name).write_text('test-only')
        (self.root / '.env').write_text("APP_DATABASE_URL='jdbc:sqlite:./data/jobtracker.db'\nSESSION_SECRET='dummy$secret'\n")
        self.database = sqlite3.connect(str(self.root / 'data/jobtracker.db'))
        self.addCleanup(self.database.close)
        self.database.execute('PRAGMA journal_mode=WAL')
        self.database.execute('PRAGMA wal_autocheckpoint=0')
        self.database.execute('create table account (value text)')
        self.database.execute("insert into account values ('latest committed data')")
        self.database.commit()

    def snapshot(self, kind):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            backup.snapshot(self.root, 'a' * 32, kind)
        metadata = json.loads(output.getvalue())
        archive = self.root / 'run' / ('local-backup-' + 'a' * 32) / 'snapshot.tar.gz'
        self.assertEqual(backup.sha256(archive), metadata['sha256'])
        self.assertNotIn('dummy$secret', output.getvalue())
        return archive

    def test_full_snapshot_contains_restore_files_and_committed_wal_data(self):
        archive = self.snapshot('full')
        with tarfile.open(archive) as package:
            names = set(package.getnames())
            self.assertTrue({'job-tracker.jar', 'runtime.tar.gz', '.env', 'data/jobtracker.db', 'start.sh', 'manifest.json'} <= names)
            self.assertFalse(any(name.endswith(('-wal', '-shm')) for name in names))
            target = self.root / 'restored.db'
            target.write_bytes(package.extractfile('data/jobtracker.db').read())
            manifest = json.load(package.extractfile('manifest.json'))
            self.assertEqual(manifest['jarSha256'], backup.sha256(self.root / 'job-tracker.jar'))
            for name, digest in manifest['files'].items():
                import hashlib
                self.assertEqual(hashlib.sha256(package.extractfile(name).read()).hexdigest(), digest)
        with sqlite3.connect(str(target)) as restored:
            self.assertEqual(restored.execute('select value from account').fetchone()[0], 'latest committed data')
            self.assertEqual(restored.execute('PRAGMA quick_check').fetchone()[0], 'ok')

    def test_hourly_snapshot_excludes_jar_and_runtime(self):
        with tarfile.open(self.snapshot('database')) as package:
            self.assertEqual(set(package.getnames()), {'.env', 'data/jobtracker.db', 'manifest.json'})

    def test_wrong_database_path_fails_and_cleans_staging(self):
        (self.root / '.env').write_text('APP_DATABASE_URL=jdbc:sqlite:./other.db\n')
        with self.assertRaises(ValueError):
            self.snapshot('database')
        self.assertFalse((self.root / 'run' / ('local-backup-' + 'a' * 32)).exists())

    def test_missing_runtime_fails_instead_of_publishing_incomplete_full_backup(self):
        (self.root / 'runtime/bin/java').unlink()
        with self.assertRaises(ValueError):
            self.snapshot('full')
        self.assertFalse((self.root / 'run' / ('local-backup-' + 'a' * 32)).exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
