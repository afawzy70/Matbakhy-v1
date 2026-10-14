"""BR-057 / TC-057 guardrail for the web-preview operator script (MB-039).

deploy.sh records the time of the last good database backup for the BackupStale alert. A backup counts only after it
has been restored into an empty scratch database and reconciled, so the metric must be written after a successful
restore and never after a failed one. Docker is replaced by a stub that records each command; no container runs.
"""
import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

STUB = """#!/bin/sh
# Test stub for docker: records the command; lists one dump; fails the restore when FAIL_RESTORE=1.
echo "$*" >> "$STUB_LOG"
case "$*" in
  *"ls /backups/"*) echo "/backups/db-X/matbakhy-X.dump" ;;
  *"RESTORE_CONFIRM_TARGET=matbakhy_restore_check"*) cat > /dev/null; [ "${FAIL_RESTORE:-0}" = 1 ] && exit 1 ;;
  *" sh -s "*) cat > /dev/null ;;
esac
exit 0
"""


class PreviewBackupTest(unittest.TestCase):
    def run_backup(self, fail_restore):
        tmp = pathlib.Path(tempfile.mkdtemp(prefix="preview-backup-"))
        self.addCleanup(shutil.rmtree, tmp)
        preview = tmp / "deploy" / "web-preview"
        preview.mkdir(parents=True)
        shutil.copy(ROOT / "deploy" / "web-preview" / "deploy.sh", preview / "deploy.sh")
        (tmp / "scripts").mkdir()
        for name in ("db-backup.sh", "db-restore.sh"):
            shutil.copy(ROOT / "scripts" / name, tmp / "scripts" / name)
        backups = tmp / "backups"
        (preview / ".env").write_text(f"BACKUP_DIR={backups}\n")
        bin_dir = tmp / "bin"
        bin_dir.mkdir()
        stub = bin_dir / "docker"
        stub.write_text(STUB)
        stub.chmod(stub.stat().st_mode | stat.S_IXUSR)
        log = tmp / "docker.log"
        env = dict(os.environ, PATH=f"{bin_dir}:{os.environ['PATH']}", STUB_LOG=str(log), FAIL_RESTORE="1" if fail_restore else "0")
        result = subprocess.run(["sh", "deploy.sh", "backup"], cwd=preview, env=env, capture_output=True, text=True)
        calls = log.read_text().splitlines() if log.exists() else []
        return result, calls, backups / "metrics" / "matbakhy_backup.prom"

    def test_metric_is_written_only_after_the_backup_was_restored_and_reconciled(self):
        result, calls, metric = self.run_backup(fail_restore=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        dump = next(i for i, c in enumerate(calls) if "PGDATABASE=matbakhy" in c)
        restore = next(i for i, c in enumerate(calls) if "RESTORE_CONFIRM_TARGET=matbakhy_restore_check" in c)
        dropped = [i for i, c in enumerate(calls) if "dropdb" in c and "matbakhy_restore_check" in c]
        self.assertLess(dump, restore, "the restore check follows the dump")
        self.assertTrue(dropped and dropped[0] < restore < dropped[-1], "the scratch database is dropped before and after")
        self.assertIn("/reconcile.sql", calls[restore])
        self.assertTrue(metric.exists())
        self.assertIn("restored and reconciled", metric.read_text())
        self.assertIn("backup verified", result.stdout)

    def test_a_failed_restore_leaves_no_metric_and_fails_the_command(self):
        result, calls, metric = self.run_backup(fail_restore=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(any("RESTORE_CONFIRM_TARGET=matbakhy_restore_check" in c for c in calls))
        self.assertFalse(metric.exists(), "an unverified backup must not count as the last good backup")
        self.assertFalse(any("stop storage" in c for c in calls), "the storage archive is not taken after a failed database backup")


if __name__ == "__main__":
    unittest.main()
